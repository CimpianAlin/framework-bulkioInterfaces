package bulkio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import org.omg.CORBA.TCKind;
import org.ossie.properties.AnyUtils;
import CF.DataType;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import BULKIO.PrecisionUTCTime;
import BULKIO.StreamSRI;
import BULKIO.PortStatistics;
import BULKIO.PortUsageType;
import org.apache.log4j.Logger;

import bulkio.sriState;
import bulkio.linkStatistics;
import bulkio.DataTransfer;
import bulkio.Int32Size;



/**
 * 
 */
public class InInt32Port extends BULKIO.jni.dataLongPOA {

    /**
     * A class to hold packet data.
     * 
     */
    public class Packet extends DataTransfer < int[] > {

	public Packet(int[] data, PrecisionUTCTime time, boolean endOfStream, String streamID, StreamSRI H, boolean sriChanged, boolean inputQueueFlushed ) {
	    super(data,time,endOfStream,streamID,H,sriChanged,inputQueueFlushed); 
	};
    };

    /**
     * 
     */
    protected String name;
     
    /**
     * 
     */
    protected linkStatistics stats;

    /**
     * 
     */
    protected Object sriUpdateLock;

    /**
     * 
     */
    protected Object statUpdateLock;

    /**
     * 
     */
    protected Map<String, sriState> currentHs;

    /**
     * 
     */
    protected Object dataBufferLock;
    
    /**
     * 
     */
    protected int maxQueueDepth;
    
    /**
     * 
     */
    protected Semaphore queueSem;

    /**
     * 
     */
    protected Semaphore dataSem;

    /**
     * 
     */
    protected boolean blocking;

    /**
     *
     */
    protected Logger   logger = null;

    protected bulkio.sri.Comparator    sri_cmp;

    protected bulkio.SriListener   streamCB;


    /**
     * This queue stores all packets received from pushPacket.
     * 
     */
    private ArrayDeque< Packet > workQueue;
    
    /**
     * 
     */
    public InInt32Port( String portName ) {
	this( portName, null, new bulkio.sri.DefaultComparator(), null );
    }

    public InInt32Port( String portName,
		       bulkio.sri.Comparator compareSRI ){
	this( portName, null, compareSRI, null );
    }

    public InInt32Port( String portName, 
			bulkio.sri.Comparator compareSRI, 
			bulkio.SriListener streamCB
		       ) {
	this( portName, null, compareSRI, streamCB );
    }

    public InInt32Port( String portName, Logger logger ) {
	this( portName, logger, new bulkio.sri.DefaultComparator(), null );
    }

    public InInt32Port( String portName, 
		       Logger logger,
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener streamCB ) {
        this.name = portName;
	this.logger = logger;
        this.stats = new linkStatistics(this.name, new Int32Size() );
        this.sriUpdateLock = new Object();
        this.statUpdateLock = new Object();
        this.currentHs = new HashMap<String, sriState>();
        this.dataBufferLock = new Object();
        this.maxQueueDepth = 100;
        this.queueSem = new Semaphore(this.maxQueueDepth);
        this.dataSem = new Semaphore(0);
        this.blocking = false;

	this.workQueue = new  ArrayDeque< Packet >();

	sri_cmp = compareSRI;	
	streamCB = streamCB;

	if ( this.logger != null ) {
	    this.logger.debug( "bulkio::InPort CTOR port: " + portName ); 
	}	
    }


    public void setLogger( Logger newlogger ){
        synchronized (this.sriUpdateLock) {
	    logger = newlogger;
	}
    }


    /**
     * 
     */
    public void setNewStreamListener( bulkio.SriListener streamCB ) {
        synchronized(this.sriUpdateLock) {
	    this.streamCB = streamCB;
	}
    }

    /**
     * 
     */
    public String getName() {
        return this.name;
    }


    /**
     * 
     */
    public void enableStats(boolean enable) {
        this.stats.setEnabled(enable);
    }

    /**
     * 
     */
    public PortStatistics statistics() {
        synchronized (statUpdateLock) {
            return this.stats.retrieve();
        }
    }

    /**
     * 
     */
    public PortUsageType state() {
        int queueSize = 0;
        synchronized (dataBufferLock) {
            queueSize = workQueue.size();
	    if (queueSize == maxQueueDepth) {
		return PortUsageType.BUSY;
	    } else if (queueSize == 0) {
		return PortUsageType.IDLE;
	    }
	    return PortUsageType.ACTIVE;
	}
    }

    /**
     * 
     */
    public StreamSRI[] activeSRIs() {
        synchronized (this.sriUpdateLock) {
            ArrayList<StreamSRI> sris = new ArrayList<StreamSRI>();
            Iterator<sriState> iter = this.currentHs.values().iterator();
            while(iter.hasNext()) {
                sris.add(iter.next().getSRI());
            }
            return sris.toArray(new StreamSRI[sris.size()]);
        }
    }
    
    /**
     * 
     */
    public int getCurrentQueueDepth() {
        synchronized (this.dataBufferLock) {
            return workQueue.size();
        }
    }
    
    /**
     * 
     */
    public int getMaxQueueDepth() {
        synchronized (this.dataBufferLock) {
            return this.maxQueueDepth;
        }
    }
    
    /**
     * 
     */
    public void setMaxQueueDepth(int newDepth) {
        synchronized (this.dataBufferLock) {
            this.maxQueueDepth = newDepth;
            queueSem = new Semaphore(newDepth);
        }
    }


    /**
     * 
     */
    public void pushSRI(StreamSRI header) {

	if ( logger != null ) {
	    logger.trace("bulkio.InPort pushSRI  ENTER (port=" + name +")" );
	}

        synchronized (sriUpdateLock) {
            if (!currentHs.containsKey(header.streamID)) {
		if ( logger != null ) {
		    logger.debug("pushSRI PORT:" + name + " NEW SRI:" + 
				 header.streamID );
		}
                if ( streamCB != null ) { streamCB.newSRI(header); }
                currentHs.put(header.streamID, new sriState(header, true));
                if (header.blocking) {
                    //If switching to blocking we have to set the semaphore
                    synchronized (dataBufferLock) {
                        if (!blocking) {
                                try {
                                    queueSem.acquire(workQueue.size());
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                        }
                        blocking = true;
                    }
                }
            } else {
                StreamSRI oldSri = currentHs.get(header.streamID).getSRI();
		boolean cval = false;
		if ( sri_cmp != null ) {
		    cval = sri_cmp.compare( header, oldSri );
		}
                if ( cval == false ) {
		    if ( streamCB != null ) { streamCB.changedSRI(header); }
                    this.currentHs.put(header.streamID, new sriState(header, true));
                    if (header.blocking) {
                        //If switching to blocking we have to set the semaphore
                        synchronized (dataBufferLock) {
                            if (!blocking) {
                                    try {
                                        queueSem.acquire(workQueue.size());
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                            }
                            blocking = true;
                        }
                    }
                }
            }
        }
	if ( logger != null ) {
	    logger.trace("bulkio.InPort pushSRI  EXIT (port=" + name +")" );
	}
    }

    

    /**
     * 
     */
    public void pushPacket(int[] data, PrecisionUTCTime time, boolean eos, String streamID) 
    {
	if ( logger != null ) {
	    logger.trace("bulkio.InPort pushPacket ENTER (port=" + name +")" );
	}
        synchronized (this.dataBufferLock) {
            if (this.maxQueueDepth == 0) {
		if ( logger != null ) {
		    logger.trace("bulkio.InPort pushPacket EXIT (port=" + name +")" );
		}
                return;
            }
        }

        boolean portBlocking = false;
        StreamSRI tmpH = new StreamSRI(1, 0.0, 1.0, (short)1, 0, 0.0, 0.0, (short)0, (short)0, streamID, false, new DataType[0]);
        boolean sriChanged = false;
        synchronized (this.sriUpdateLock) {
            if (this.currentHs.containsKey(streamID)) {
                tmpH = this.currentHs.get(streamID).getSRI();
                sriChanged = this.currentHs.get(streamID).isChanged();
		if ( eos == false ) {
		    this.currentHs.get(streamID).setChanged(false);
		}
                portBlocking = blocking;
            }
        }


        // determine whether to block and wait for an empty space in the queue
        Packet p = null;

        if (portBlocking) {
            p = new Packet(data, time, eos, streamID, tmpH, sriChanged, false);

            try {
                queueSem.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            synchronized (this.dataBufferLock) {
                this.stats.update(data.length, this.workQueue.size()/this.maxQueueDepth, eos, streamID, false);
                this.workQueue.add(p);
                this.dataSem.release();
            }
        } else {
            synchronized (this.dataBufferLock) {
                if (this.workQueue.size() == this.maxQueueDepth) {
		    if ( logger != null ) {
			logger.debug( "bulkio::InPort pushPacket PURGE INPUT QUEUE (SIZE"  + this.workQueue.size() + ")" );
		    }
                    boolean sriChangedHappened = false;
                    for (Iterator< Packet > itr = this.workQueue.iterator(); itr.hasNext();) {
                        if (itr.next().sriChanged) {
                            sriChangedHappened = true;
                            break;
                        }
                    }
                    if (sriChangedHappened) {
                        sriChanged = true;
                    }
                    this.workQueue.clear();
                    p = new Packet( data, time, eos, streamID, tmpH, sriChanged, true);
                    this.stats.update(data.length, 0, eos, streamID, true);
                } else {
                    p = new Packet(data, time, eos, streamID, tmpH, sriChanged, false);
                    this.stats.update(data.length, this.workQueue.size()/this.maxQueueDepth, eos, streamID, false);
                }
		if ( logger != null ) {
		    logger.trace( "bulkio::InPort pushPacket NEW Packet (QUEUE=" + workQueue.size() + ")");
		}
                this.workQueue.add(p);
                this.dataSem.release();
            }
        }

	if ( logger != null ) {
	    logger.trace("bulkio.InPort pushPacket EXIT (port=" + name +")" );
	}
        return;

    }
     
    /**
     * 
     */
    public Packet getPacket(long wait) 
    {

	if ( logger != null ) {
	    logger.trace("bulkio.InPort getPacket ENTER (port=" + name +")" );
	}

        try {
            if (wait < 0) {
		if ( logger != null ) {
		    logger.trace("bulkio.InPort getPacket PORT:" + name +" Block until data arrives" );
		}
                this.dataSem.acquire();
            } else {
		if ( logger != null ) {
		    logger.trace("bulkio.InPort getPacket PORT:" + name +" TIMED WAIT:" + wait );
		}
                this.dataSem.tryAcquire(wait, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
	    if ( logger != null ) {
		logger.trace("bulkio.InPort getPacket EXIT (port=" + name +")" );
	    }
            return null;
        }
        
        Packet p = null;
        synchronized (this.dataBufferLock) {
            p = this.workQueue.poll();
        }

        if (p != null) {
            if (p.getEndOfStream()) {
                synchronized (this.sriUpdateLock) {
                    if (this.currentHs.containsKey(p.getStreamID())) {
                        sriState rem = this.currentHs.remove(p.getStreamID());

                        if (rem.getSRI().blocking) {
                            boolean stillBlocking = false;
                            Iterator<sriState> iter = currentHs.values().iterator();
                            while (iter.hasNext()) {
                            	if (iter.next().getSRI().blocking) {
                                    stillBlocking = true;
                                    break;
                                }
                            }

                            if (!stillBlocking) {
                                blocking = false;
                            }
                        }
                    }
                }
            }
            
            if (blocking) {
                queueSem.release();
            }
        }

	if ( logger != null ) {
	    logger.trace("bulkio.InPort getPacket EXIT (port=" + name +")" );
	}
        return p;
    }


}

