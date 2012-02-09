package blue.mesh;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;


public class RouterObject {

	private List <String> connectedDevices;
	private List <ReadWriteThread> rwThreads;
	private List <byte[]> messageIDs;
	private final String TAG = "RouterObject";
	private List <byte[]> messages;
	
	protected RouterObject( BluetoothAdapter mAdapter) {
		
	}
	
	protected synchronized int beginConnection(BluetoothSocket socket) {
		
		//Don't let another thread touch connectedDevices while
		//I read and write it
		synchronized( this.connectedDevices ){
			//Check if the device is already connected to
			if( connectedDevices.contains(socket.getRemoteDevice().getName())){
				try {
					socket.close();
				} catch (IOException e) {
					Log.e(TAG, "could not close() socket", e);
				}
				return Constants.SUCCESS;
			}
			//Add device name to list of connected devices
			connectedDevices.add(socket.getRemoteDevice().getName());
		}
		
		//Don't let another thread touch rwThreads while I add to it
		ReadWriteThread aReadWriteThread = new ReadWriteThread(this, socket);
		aReadWriteThread.start();
		synchronized( this.rwThreads ){
			rwThreads.add(aReadWriteThread);
		}
		
		return Constants.SUCCESS;
	}
	
	protected int route(byte buffer[]) {
		
		byte messageID[] = new byte[Constants.MESSAGE_ID_LEN];
		for( int i = 0; i < Constants.MESSAGE_ID_LEN; i++ ){
			messageID[i] = buffer[i];
		}
		
		//Check that the message was not received before
		synchronized( this.messageIDs ){
			if( messageIDs.contains(messageID) ){
				return Constants.SUCCESS;
			}
			else{
				messageIDs.add(messageID);
				//Remove oldest message ID if too many are stored
				if( messageIDs.size() > Constants.MSG_HISTORY_LEN){
					messageIDs.remove(0);
				}
			}
		}
		
		//Send the message all the threads
		synchronized( this.rwThreads ){
			for( ReadWriteThread aThread : rwThreads ){
				aThread.write(buffer);
			}
		}
		
		//Add message to buffer
		synchronized( this.messages ){
			byte message[] = new byte[buffer.length - Constants.MESSAGE_ID_LEN];
			for( int i = Constants.MESSAGE_ID_LEN; i < buffer.length; i++ ){
				message[i - Constants.MESSAGE_ID_LEN] = buffer[i];
			}
			messages.add(buffer);
		}
		
		return Constants.SUCCESS;
	}
	
	protected byte [] getNextMessage() {
		if(messages.size() > 0){
			byte message[] = messages.get(0);
			messages.remove(0);
			return message;
		}
		else{
			return null;
		}
	}
	
	protected int getDeviceState( BluetoothDevice device ){
		synchronized( this.connectedDevices ){
			if( connectedDevices.contains(device.getName()) ){
				return Constants.STATE_CONNECTED;
			}
		}
		return Constants.STATE_NONE;
	}
	
	protected int stop() {
		
		for( ReadWriteThread aThread : rwThreads ){
			aThread.interrupt();
			try {
				aThread.getSocket().close();
			} catch (IOException e) {
				Log.e(TAG, "could not close socket", e);
			}
		}
		
		return Constants.SUCCESS;
	}
	
	protected int write( byte[] buffer ){
		
		Random rand = new Random();
		byte messageID[] = new byte[Constants.MESSAGE_ID_LEN];
		rand.nextBytes(messageID);
		
		byte new_buffer[] = new byte[Constants.MESSAGE_ID_LEN + buffer.length];
		
		for( int i = 0; i < Constants.MESSAGE_ID_LEN; i++ ){
			new_buffer[i] = messageID[i];
		}
		
		for( int i = 0; i < buffer.length; i++ ){
			new_buffer[Constants.MESSAGE_ID_LEN + i] = buffer[i];
		}
		
		return Constants.SUCCESS;
	}
}

