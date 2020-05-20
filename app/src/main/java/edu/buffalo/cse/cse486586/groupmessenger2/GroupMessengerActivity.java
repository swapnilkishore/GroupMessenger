package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    static final int SERVER_PORT = 10000;
    static int senderSequence = 0;
    int counter = 0;
    static PriorityQueue<Message> deliveryQueue = new PriorityQueue<Message>(10);
    int proposedPriority = 0;
    String failedport;

    //FUNCTION TO CALCULATE MAX
    int max(int n1, int n2)
    {
        if(n1 > n2)
            return n1;
        else
            return n2;
    }
    //FUNCTION TO CLEANUP QUEUE WHEN A FAILED AVD IS DETECTED
    void cleanup(String port)
    {
        for(Message temp : deliveryQueue)
        {
            if(temp.getPort() == Integer.parseInt(port))
                deliveryQueue.remove(temp);
        }
        Log.e(TAG, "IN CLEANUP");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        //Code taken from PA1
        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "IOException in server socket");
        }

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final Button send = (Button) findViewById(R.id.button4);
        final EditText editText = (EditText) findViewById(R.id.editText1);

        send.setOnClickListener(new Button.OnClickListener() {

            @Override
            public void onClick(View v) {
                String message = editText.getText().toString() + "\n";
                editText.setText("");
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append("\t" + message);
                TextView remoteTextView = (TextView) findViewById(R.id.textView1);
                remoteTextView.append("\n");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, myPort);
                Log.e(TAG, "Send is pressed and ClientTask is called");

            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... serverSockets) {
            Message serverMessage;
            ObjectInputStream inputStream;
            ObjectOutputStream outputStream;
            while (true) {
                try {
                    ServerSocket serverSocket = serverSockets[0];
                    Socket server = serverSocket.accept();
                    inputStream = new ObjectInputStream(server.getInputStream());
                    outputStream = new ObjectOutputStream(server.getOutputStream());
                    serverMessage = (Message) inputStream.readObject();
                    if (serverMessage.getMessage() == null) {
                        failedport = String.valueOf(serverMessage.getPort());
                        cleanup(failedport);
                    } else {
                        Log.e(TAG, "INITIAL MESSAGE RECEIVED!!!!");
                        proposedPriority = max(proposedPriority, serverMessage.getMessagePriority()) + 1;
                        serverMessage.setProposedPriority(proposedPriority);
                        serverMessage.setDeliveryStatus(false);
                        serverMessage.setPort(serverMessage.getPort());
                        outputStream.writeObject(serverMessage);
                        Log.e(TAG, "SENT WITH PROPOSED PRIORITY BACK TO SENDER!!!!");
                        outputStream.flush();
                    }

                    serverMessage = (Message) inputStream.readObject();
                    if (serverMessage.getMessage() == null) {
                        failedport = String.valueOf(serverMessage.getPort());
                        cleanup(failedport);
                    } else {
                        serverMessage.setDeliveryStatus(true);
                        Log.e(TAG, "RECIEVED WITH FINAL AGREED PRIORITY");
                        deliveryQueue.add(serverMessage);

                    }

                    while (deliveryQueue.peek() != null && deliveryQueue.peek().getDeliveryStatus()) {
                        serverMessage = deliveryQueue.poll();
                        publishProgress(serverMessage.getMessage());
                    }
                } catch (EOFException e) {
                    Log.e(TAG, "EOFExceptiom im server task");
                } catch (StreamCorruptedException e) {
                    Log.e(TAG, "StreamCorrupted during server task");
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "SocketTImeout occured in server task");
                } catch (IOException e) {
                    Log.e(TAG, "IOExceptiom in server task");
                } catch (Exception e) {
                    Log.e(TAG, "Exception occured in server task");
                }
            }
        }

        @Override
        protected void onProgressUpdate(String... strings) {

            int key = counter++;
            //CODE TAKEN FROM PA2A
            String text = strings[0];
            Uri newUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            ContentValues cv = new ContentValues();
            cv.put(KEY_FIELD, Integer.toString(key));
            cv.put(VALUE_FIELD, text);

            try {
                getContentResolver().insert(newUri, cv);
                Log.e(TAG, "Inserting into contentprovider");
            } catch (Exception e)
            {
                Log.e(TAG, "Error in insertion");
            }
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strings[0] + "\n");
            return;
        }
    }

    //CODE TAKEN FROM PA2A

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            Socket sockets[] = new Socket[5];
            Socket socket;
            String clientMessage = strings[0];
            int count = 0;
            senderSequence++;

            //CREATING AN ARRAY OF OUTPUTSTREAMS AND INPUTSTREANS AS SUGGESTED BY TA
            ObjectInputStream inputStreams[] = new ObjectInputStream[5];
            ObjectOutputStream outputStreams[] = new ObjectOutputStream[5];
            ObjectInputStream inputStream;
            ObjectOutputStream outputStream;

            Message message;
            int final_seq = -2;
            int port = Integer.valueOf(strings[1]);

            for (String remotePort : REMOTE_PORTS) {
                try {
                    sockets[count] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(remotePort));
                    socket = sockets[count];
                    outputStreams[count] = new ObjectOutputStream(socket.getOutputStream());
                    inputStreams[count] = new ObjectInputStream(socket.getInputStream());
                    outputStream = outputStreams[count];
                    inputStream = inputStreams[count];
                    //SETSOTIMEOUT TO CREATE A BLOCKING READ CALL ON THE SOCKET.
                    socket.setSoTimeout(3000);
                    message = new Message();
                    message.setMessage(clientMessage);
                    message.setMessagePriority(senderSequence);
                    message.setPort(port);
                    outputStream.writeObject(message);
                    outputStream.flush();
                    Log.e(TAG, "SENT TO ONE PORT");
                    message = (Message) inputStream.readObject();
                    if(message.getMessage() == null)
                    {
                        failedport = remotePort;
                        Log.e(TAG, "Failed message in first loop");
                        cleanup(failedport);
                    }
                    else {
                        Log.e(TAG, "READ INCOMING MESSAGE WITH PROPOSED PRIORITY");
                        final_seq = max(final_seq, message.getProposedPriority());
                    }
                    count++;
                } catch (UnknownHostException e) {
                    Log.e(TAG, "UnknownHostExceptiom");
                } catch (EOFException e) {
                    Log.e(TAG, "EOFException occured");
                } catch (StreamCorruptedException e) {
                    Log.e(TAG, "StreamCorruptedException");
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "SocketTimeout occured in first loop");
                } catch (IOException e) {
                    Log.e(TAG, "IOException occured");
                } catch (Exception e) {
                    Log.e(TAG, "Exception occured");
                }
            }

            //CREATING THE FINAL MESSAGE WITH THE AGREED FINAL PRIORITY
            Message final_msg = new Message();
            final_msg.setMessage(clientMessage);
            final_msg.setMessagePriority(senderSequence);
            int agreed = max(senderSequence, final_seq);
            senderSequence = agreed;
            final_msg.setDecidedPriority(agreed);
            final_msg.setPort(port);

            //FINALLY MULTICASTING THE MESSAGE
            for (int i = 0; i < REMOTE_PORTS.length; i++) {
                try {
                    socket = sockets[i];
                    outputStream = outputStreams[i];
                    outputStream.writeObject(final_msg);
                    outputStream.flush();
                    socket.close();
                    Log.e(TAG, "FINALLY SENT");
                } catch (UnknownHostException e) {
                    Log.e(TAG, "UnknownHostExceptiom");
                } catch (EOFException e) {
                    Log.e(TAG, "EOFException occured");
                } catch (StreamCorruptedException e) {
                    Log.e(TAG, "StreamCorruptedException");
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "SocketTimeout occured in first loop");
                } catch (IOException e) {
                    Log.e(TAG, "IOException occured");
                } catch (Exception e) {
                    Log.e(TAG, "Exception occured");
                }
            }
            return null;
        }
    }
}
