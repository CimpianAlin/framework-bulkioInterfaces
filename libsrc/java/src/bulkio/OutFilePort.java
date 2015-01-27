/*
 * This file is protected by Copyright. Please refer to the COPYRIGHT file
 * distributed with this source distribution.
 *
 * This file is part of REDHAWK bulkioInterfaces.
 *
 * REDHAWK bulkioInterfaces is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * REDHAWK bulkioInterfaces is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package bulkio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.log4j.Logger;
import CF.DataType;
import BULKIO.PrecisionUTCTime;
import BULKIO.StreamSRI;
import BULKIO.dataFileOperations;

import bulkio.linkStatistics;
import bulkio.Int8Size;
import bulkio.ConnectionEventListener;
import bulkio.connection_descriptor_struct;
import bulkio.SriMapStruct;
import org.ossie.properties.*;

/**
 * 
 */
public class OutFilePort extends OutPortBase<dataFileOperations> {

    protected List<connection_descriptor_struct> filterTable = null;


    public OutFilePort(String portName ){
	this( portName, null, null );
    }

    public OutFilePort(String portName,
		       Logger logger ) {
	this( portName, logger, null );
    }

    /**
     * @generated
     */
    public OutFilePort(String portName,
		       Logger logger,
		       ConnectionEventListener  eventCB ) {
        super(portName, logger, eventCB);
        filterTable = null;
        if ( this.logger != null ) {
            this.logger.debug( "bulkio.OutPort CTOR port: " + portName ); 
        }
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

        if (header.streamID == null) {
            throw new NullPointerException("SRI streamID cannot be null");
        }

        // Header cannot have null keywords
        if (header.keywords == null) header.keywords = new DataType[0];

        synchronized(this.updatingPortsLock) {    // don't want to process while command information is coming in
            this.currentSRIs.put(header.streamID, new SriMapStruct(header));
            if (this.active) {
		// state if this port is not listed in the filter table... then pushSRI down stream
		boolean portListed = false;

		// for each connection
                for (Entry<String, dataFileOperations> p : this.outConnections.entrySet()) {
		    
		    // if connection is in the filter table
                    for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {

			// if there is an entry for this port in the filter table....so save that state
			if (ftPtr.port_name.getValue().equals(this.name)) {
			    portListed = true;		    
			}

			if ( logger != null ) {
			    logger.trace( "pushSRI - FilterMatch port:" + this.name + " connection:" + p.getKey() + 
					    " streamID:" + header.streamID ); 
			}

			if ( (ftPtr.port_name.getValue().equals(this.name)) &&
			     (ftPtr.connection_id.getValue().equals(p.getKey())) &&
			     (ftPtr.stream_id.getValue().equals(header.streamID))) {
                            try {
				if ( logger != null ) {
				    logger.trace( "pushSRI - FilterMatch port:" + this.name + " connection:" + p.getKey() + 
						  " streamID:" + header.streamID ); 
				}
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
		    for (Entry<String, dataFileOperations> p : this.outConnections.entrySet()) {
                        try {
			    if ( logger != null ) {
				logger.trace( "pushSRI - NO Filter port:" + this.name + " connection:" + p.getKey() + 
					      " streamID:" + header.streamID ); 
			    }
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
    };

    /**
     * @generated
     */
    public void pushPacket(String data, PrecisionUTCTime time, boolean endOfStream, String streamID)
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
            String odata = data;
            if (this.active) {
		boolean portListed = false;
                for (Entry<String, dataFileOperations> p : this.outConnections.entrySet()) {

                    for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {

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
                                this.stats.get(p.getKey()).update( odata.length(), (float)0.0, endOfStream, streamID, false);
                            } catch(Exception e) {
                                if ( logger != null ) {
                                    logger.error("Call to pushPacket failed on port " + name + " connection " + p.getKey() );
                                }
                            }
                        }
                    }
                }

		if (!portListed ){
		    for (Entry<String, dataFileOperations> p : this.outConnections.entrySet()) {
                        try {
                            //If SRI for given streamID has not been pushed to this connection, push it
                            if (!sriStruct.connections.contains(p.getKey())){
                                p.getValue().pushSRI(sriStruct.sri);
                                sriStruct.connections.add(p.getKey());
                            }
                            p.getValue().pushPacket( odata, time, endOfStream, streamID);
                            this.stats.get(p.getKey()).update( odata.length(), (float)0.0, endOfStream, streamID, false);
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
            final dataFileOperations port;
            try {
                port = BULKIO.jni.dataFileHelper.narrow(connection);
            } catch (final Exception ex) {
                if ( logger != null ) {
                    logger.error("bulkio.OutPort CONNECT PORT: " + name + " PORT NARROW FAILED");
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
            for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {
                if (ftPtr.port_name.getValue().equals(this.name)) {
                    portListed = true;
                    break;
                }
            }
            dataFileOperations port = this.outConnections.remove(connectionId);
            if (port != null)
            {
                String odata = "";
                BULKIO.PrecisionUTCTime tstamp = bulkio.time.utils.notSet();
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
	    callback.disconnect(connectionId);
	}

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort disconnectPort EXIT (port=" + name +")" );
	}
    }

}

