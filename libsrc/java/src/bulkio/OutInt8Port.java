package bulkio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.omg.CORBA.TCKind;
import org.ossie.properties.AnyUtils;
import org.apache.log4j.Logger;
import CF.DataType;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import BULKIO.PrecisionUTCTime;
import BULKIO.StreamSRI;
import BULKIO.UsesPortStatistics;
import ExtendedCF.UsesConnection;
import BULKIO.PortUsageType;
import BULKIO.dataCharOperations;
import bulkio.linkStatistics;
import bulkio.Int8Size;
import bulkio.ConnectionEventListener;
import bulkio.connection_descriptor_struct;
import bulkio.SriMapStruct;
import org.ossie.properties.*;

/**
 * 
 */
public class OutInt8Port extends BULKIO.UsesPortStatisticsProviderPOA {

    /**
     * @generated
     */
    protected String name;

    /**
     * @generated
     */
    protected Object updatingPortsLock;

    /**
     * @generated
     */
    protected boolean active;

    /**
     * Map of connection Ids to port objects
     * @generated
     */
    protected Map<String, dataCharOperations> outConnections = null;

    /**
     * Map of connection ID to statistics
     * @generated
     */
    protected Map<String, linkStatistics > stats;

    /**
     * Map of stream IDs to streamSRI's
     * @generated
     */
    protected Map<String, SriMapStruct > currentSRIs;

    /**
     *
     */
    protected Logger   logger = null;


    /**
     * Event listener when connect/disconnet events happen
     */
    protected ConnectionEventListener   callback = null;

    protected List<connection_descriptor_struct> filterTable = null;


    public OutInt8Port(String portName ){
	this( portName, null, null );
    }

    public OutInt8Port(String portName,
		       Logger logger ) {
	this( portName, logger, null );
    }

    /**
     * @generated
     */
    public OutInt8Port(String portName,
		       Logger logger,
		       ConnectionEventListener  eventCB ) {
        name = portName;
        updatingPortsLock = new Object();
        active = false;
	outConnections = new HashMap<String, dataCharOperations>();
        stats = new HashMap<String, linkStatistics >();
        currentSRIs = new HashMap<String, SriMapStruct>();
        callback = eventCB;
        this.logger = logger;
        filterTable = null;
	if ( this.logger != null ) {
	    this.logger.debug( "bulkio::OutPort CTOR port: " + portName ); 
	}
    }


    public void setLogger( Logger newlogger ){
        synchronized (this.updatingPortsLock) {
	    logger = newlogger;
	}
    }

    public void setConnectionEventListener( ConnectionEventListener newListener ){
        synchronized (this.updatingPortsLock) {
	    callback = newListener;
	}
    }

    /**
     * @generated
     */
    public PortUsageType state() {
        PortUsageType state = PortUsageType.IDLE;

        if (this.outConnections.size() > 0) {
            state = PortUsageType.ACTIVE;
        }

        return state;
    }

    /**
     * @generated
     */
    public void enableStats(final boolean enable)
    {
        for (String connId : outConnections.keySet()) {
            stats.get(connId).setEnabled(enable);
        }
    };

    /**
     * @generated
     */
    public UsesPortStatistics[] statistics() {
        UsesPortStatistics[] portStats = new UsesPortStatistics[this.outConnections.size()];
        int i = 0;

        synchronized (this.updatingPortsLock) {
            for (String connId : this.outConnections.keySet()) {
                portStats[i++] = new UsesPortStatistics(connId, this.stats.get(connId).retrieve());
            }
        }

        return portStats;
    }

    /**
     * @generated
     */
    public StreamSRI[] activeSRIs()
    {
        ArrayList<StreamSRI> sriList = new ArrayList<StreamSRI>();
        for(Map.Entry<String, SriMapStruct > entry: this.currentSRIs.entrySet()) {
            SriMapStruct srimap = entry.getValue();
            sriList.add(srimap.sri);
        }
        return sriList.toArray(new StreamSRI[0]);
    }

    /**
     * @generated
     */
    public boolean isActive() {
        return this.active;
    }

    /**
     * @generated
     */
    public void setActive(final boolean active) {
        this.active = active;
    }

    /**
     * @generated
     */
    public String getName() {
        return this.name;
    }

    /**
     * @generated
     */
    public HashMap<String, dataCharOperations> getPorts() {
        return new HashMap<String, dataCharOperations>();
    }

    /**
     * pushSRI
     *     description: send out SRI describing the data payload
     *
     *  H: structure of type BULKIO.StreamSRI with the SRI for this stream
     *    hversion
     *    xstart: start time of the stream
     *    xdelta: delta between two samples
     *    xunits: unit types from Platinum specification
     *    subsize: 0 if the data is one-dimensional
     *    ystart
     *    ydelta
     *    yunits: unit types from Platinum specification
     *    mode: 0-scalar, 1-complex
     *    streamID: stream identifier
     *    sequence<CF::DataType> keywords: unconstrained sequence of key-value pairs for additional description
     * @generated
     */
    public void pushSRI(StreamSRI header)
    {
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort pushSRI  ENTER (port=" + name +")" );
	}

        // Header cannot be null
        if (header == null) {
	    if ( logger != null ) {
		logger.trace("bulkio.OutPort pushSRI  EXIT (port=" + name +")" );
	    }
	    return;
	}

        // Header cannot have null keywords
        if (header.keywords == null) header.keywords = new DataType[0];

        synchronized(this.updatingPortsLock) {    // don't want to process while command information is coming in
            this.currentSRIs.put(header.streamID, new SriMapStruct(header));
            if (this.active) {
		// state if this port is not listed in the filter table... then pushSRI down stream
		boolean portListed = false;

		// for each connection
                for (Entry<String, dataCharOperations> p : this.outConnections.entrySet()) {
		    
		    // if connection is in the filter table
                    for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {

			// if there is an entry for this port in the filter table....so save that state
			if (ftPtr.port_name.getValue().equals(this.name)) {
			    portListed = true;		    
			}

			if ( (ftPtr.port_name.getValue().equals(this.name)) &&
			     (ftPtr.connection_id.getValue().equals(p.getKey())) &&
			     (ftPtr.stream_id.getValue().equals(header.streamID))) {
                            try {
				p.getValue().pushSRI(header);
                                //Update entry in currentSRIs
                                this.currentSRIs.get(header.streamID).connections.add(p.getKey());

                            } catch(Exception e) {
                                if ( logger != null ) {
				    logger.error("Call to pushSRI failed on port " + name + " connection " + p.getKey() );
                                }
                            }
                        }
                    }
		}

		// no entry exists for this port in the filter table so all connections get SRI data
		if (!portListed ) {
		    for (Entry<String, dataCharOperations> p : this.outConnections.entrySet()) {
                        try {
			    p.getValue().pushSRI(header);
                            //Update entry in currentSRIs
                            this.currentSRIs.get(header.streamID).connections.add(p.getKey());
			} catch(Exception e) {
			    if ( logger != null ) {
				logger.error("Call to pushSRI failed on port " + name + " connection " + p.getKey() );
			    }
                        }
                    }
                }


            }


        }    // don't want to process while command information is coming in


	if ( logger != null ) {
	    logger.trace("bulkio.OutPort pushSRI  EXIT (port=" + name +")" );
	}
        return;
    }

    public void updateConnectionFilter(List<connection_descriptor_struct> _filterTable) {
        this.filterTable = _filterTable;
    }

    /**
     * @generated
     */
    public void pushPacket(char[] data, PrecisionUTCTime time, boolean endOfStream, String streamID)
    {

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort pushPacket  ENTER (port=" + name +")" );
	}

        if (!this.currentSRIs.containsKey(streamID)) {
            StreamSRI header = bulkio.sri.utils.create();
            header.streamID = streamID;
            this.pushSRI(header);
        }
        SriMapStruct sriStruct = this.currentSRIs.get(streamID);

        synchronized(this.updatingPortsLock) {    // don't want to process while command information is coming in
            char[] odata = data;
            if (this.active) {
		boolean portListed = false;
                for (Entry<String, dataCharOperations> p : this.outConnections.entrySet()) {

                    for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable))  {

			if (ftPtr.port_name.getValue().equals(this.name)) {
			    portListed = true;		    
			}
                        if ( (ftPtr.port_name.getValue().equals(this.name)) && 
			     (ftPtr.connection_id.getValue().equals(p.getKey())) && 
			     (ftPtr.stream_id.getValue().equals(streamID)) ) {
                            try {
                                //If SRI for given streamID has not been pushed to this connection, push it
                                if (!sriStruct.connections.contains(p.getKey())){
                                    p.getValue().pushSRI(sriStruct.sri);
                                    sriStruct.connections.add(p.getKey());
                                }
                                p.getValue().pushPacket( odata, time, endOfStream, streamID);
                                this.stats.get(p.getKey()).update( odata.length, (float)0.0, endOfStream, streamID, false);
                            } catch(Exception e) {
                                if ( logger != null ) {
                                    logger.error("Call to pushPacket failed on port " + name + " connection " + p.getKey() );
                                }
                            }
                        }
                    }
                }

		if (!portListed ){
		    for (Entry<String, dataCharOperations> p : this.outConnections.entrySet()) {
                        try {
                            //If SRI for given streamID has not been pushed to this connection, push it
                            if (!sriStruct.connections.contains(p.getKey())){
                                p.getValue().pushSRI(sriStruct.sri);
                                sriStruct.connections.add(p.getKey());
                            }
                            p.getValue().pushPacket( odata, time, endOfStream, streamID);
                            this.stats.get(p.getKey()).update( odata.length, (float)0.0, endOfStream, streamID, false);
                        } catch(Exception e) {
                            if ( logger != null ) {
                                logger.error("Call to pushPacket failed on port " + name + " connection " + p.getKey() );
                            }
                        }
                    }
                }
            }
	    if ( endOfStream ) {
		if ( this.currentSRIs.containsKey(streamID) ) {
		    this.currentSRIs.remove(streamID);
		}
	    }

        }    // don't want to process while command information is coming in

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort pushPacket  EXIT (port=" + name +")" );
	}
        return;
    }

    /**
     * @generated
     */
    public void connectPort(final org.omg.CORBA.Object connection, final String connectionId) throws CF.PortPackage.InvalidPort, CF.PortPackage.OccupiedPort
    {
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort connectPort ENTER (port=" + name +")" );
	}

        synchronized (this.updatingPortsLock) {
            final dataCharOperations port;
            try {
                port = BULKIO.jni.dataCharHelper.narrow(connection);
            } catch (final Exception ex) {
		if ( logger != null ) {
		    logger.error("bulkio::OutPort CONNECT PORT: " + name + " PORT NARROW FAILED");
		}
                throw new CF.PortPackage.InvalidPort((short)1, "Invalid port for connection '" + connectionId + "'");
            }
            this.outConnections.put(connectionId, port);
            this.active = true;
            this.stats.put(connectionId, new linkStatistics( this.name, new Int8Size() ) );

            if ( logger != null ) {
                logger.debug("bulkio.OutPort CONNECT PORT: " + name + " CONNECTION '" + connectionId + "'");
            }
        }

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort connectPort EXIT (port=" + name +")" );
	}

	if ( callback != null ) {
	    callback.connect(connectionId);
	}
    }

    /**
     * @generated
     */
    public void disconnectPort(String connectionId) {
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort disconnectPort ENTER (port=" + name +")" );
	}
        synchronized (this.updatingPortsLock) {
            boolean portListed = false;
            for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable)) {
                if (ftPtr.port_name.getValue().equals(this.name)) {
                    portListed = true;
                    break;
                }
            }
            dataCharOperations port = this.outConnections.remove(connectionId);
            if (port != null)
            {
                char[] odata = new char[0];
                BULKIO.PrecisionUTCTime tstamp = bulkio.time.utils.now();
                for (Map.Entry<String, SriMapStruct > entry: this.currentSRIs.entrySet()) {
                    String streamID = entry.getKey();
                    if (entry.getValue().connections.contains(connectionId)) {
                        if (portListed) {
                            for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable)) {
                                if ( (ftPtr.port_name.getValue().equals(this.name)) &&
	                        	 (ftPtr.connection_id.getValue().equals(connectionId)) &&
					 (ftPtr.stream_id.getValue().equals(streamID))) {
                                    try {
                                        port.pushPacket(odata,tstamp,true,streamID);
                                    } catch(Exception e) {
                                        if ( logger != null ) {
                                            logger.error("Call to pushPacket failed on port " + name + " connection " + connectionId );
                                        }
                                    }
                                }
                            }
                        } else {
                            try {
                                port.pushPacket(odata,tstamp,true,streamID);
                            } catch(Exception e) {
                                if ( logger != null ) {
                                    logger.error("Call to pushPacket failed on port " + name + " connection " + connectionId );
                                }
                            }
                        }
                    }
                }
            }
            this.stats.remove(connectionId);
            this.active = (this.outConnections.size() != 0);

            // Remove connectionId from any sets in the currentSRIs.connections values
            for(Map.Entry<String, SriMapStruct > entry :  this.currentSRIs.entrySet()) {
                entry.getValue().connections.remove(connectionId);
            }

            if ( logger != null ) {
                logger.trace("bulkio.OutPort DISCONNECT PORT:" + name + " CONNECTION '" + connectionId + "'");
                for(Map.Entry<String, SriMapStruct > entry: this.currentSRIs.entrySet()) {
                    logger.trace("bulkio.OutPort updated currentSRIs key=" + entry.getKey() + ", value.sri=" + entry.getValue().sri + ", value.connections=" + entry.getValue().connections);
                }
            }
        }

	if ( callback != null ) {
	    callback.connect(connectionId);
	}

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort disconnectPort EXIT (port=" + name +")" );
	}
    }

    /**
     * @generated
     */
    public UsesConnection[] connections() {
        final UsesConnection[] connList = new UsesConnection[this.outConnections.size()];
        int i = 0;
        synchronized (this.updatingPortsLock) {
            for (Entry<String, dataCharOperations> ent : this.outConnections.entrySet()) {
                connList[i++] = new UsesConnection(ent.getKey(), (org.omg.CORBA.Object) ent.getValue());
            }
        }
        return connList;
    }

}

