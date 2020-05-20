package edu.buffalo.cse.cse486586.groupmessenger2;
import java.io.Serializable;

public class Message implements   Serializable {
    private  String message;
    private int priority;
    private int proposedPriority;
    private int decidedPriority;
    private boolean is_deliver;
    private int port;

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public int getMessagePriority()
    {
        return priority;
    }
    public void setMessagePriority(int priority)
    {
        this.priority = priority;
    }

    public int getProposedPriority()
    {
        return proposedPriority;
    }

    public void setProposedPriority(int proposedPriority)
    {
        this.proposedPriority = proposedPriority;
    }
    public void setDecidedPriority(int decidedPriority)
    {
        this.decidedPriority = decidedPriority;
    }

    public boolean getDeliveryStatus() {
        return is_deliver;
    }

    public void setDeliveryStatus(boolean is_deliver) {
        this.is_deliver = is_deliver;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public int getPort()
    {
        return port;
    }


    public Message()
    {

    }
}
