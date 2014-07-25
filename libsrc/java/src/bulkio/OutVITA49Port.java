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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;
import org.omg.CORBA.TCKind;
import org.ossie.properties.AnyUtils;
import org.ossie.component.QueryableUsesPort;
import org.apache.log4j.Logger;
import CF.DataType;
import CF.PropertiesHelper;
import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import BULKIO.PrecisionUTCTime;
import BULKIO.StreamSRI;
import BULKIO.UsesPortStatistics;
import ExtendedCF.UsesConnection;
import BULKIO.PortUsageType;
import BULKIO.dataVITA49Package.AttachError;
import BULKIO.dataVITA49Package.DetachError;
import BULKIO.dataVITA49Package.InputUsageState;
import BULKIO.dataVITA49Package.StreamInputError;
import BULKIO.dataVITA49Operations;
import BULKIO.VITA49StreamDefinition;

import bulkio.linkStatistics;
import bulkio.Int8Size;
import bulkio.connection_descriptor_struct;
import bulkio.SriMapStruct;
import bulkio.vita49.VITA49StreamContainer;
import bulkio.vita49.VITA49Stream;
import bulkio.vita49.VITA49StreamAttachment;


/**
 * @generated
 */
public class OutVITA49Port extends BULKIO.UsesPortStatisticsProviderPOA {

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
     * @generated
     */
    protected String userId;


    /**
     * Map of connection Ids to port objects
     * @generated
     */
    protected Map<String, dataVITA49Operations> outConnections = null;

    /**
     * Map of connection ID to statistics
     * @generated
     */
    protected Map<String, linkStatistics > stats;

    /**
     * Map of stream IDs to SriMapStruct 
     * @generated
     */
    protected Map<String, SriMapStruct > currentSRIs;

    /**
     * @generated
     */
    protected VITA49StreamContainer streamContainer;

    /**
     *
     */
    protected Logger   logger = null;


    /**
     *
     */
    protected ConnectionEventListener callback = null;

    protected List<connection_descriptor_struct> filterTable = null;

    public OutVITA49Port(String portName ){
	this( portName, null, null );
    }

    public OutVITA49Port(String portName,
		       Logger logger ) {
	this( portName, logger, null );
    }

    /**
     * @generated
     */
    public OutVITA49Port(String portName,
		       Logger logger,
		       ConnectionEventListener  eventCB ) {
        this.name = portName;
        this.updatingPortsLock = new Object();
        this.active = false;
        this.outConnections = new HashMap<String, BULKIO.dataVITA49Operations>();
        this.stats = new HashMap<String, linkStatistics>();
        this.currentSRIs = new HashMap<String, SriMapStruct>();
	this.logger = logger;
        this.filterTable = null;
	this.callback = eventCB;
        this.streamContainer = new VITA49StreamContainer();
        this.userId = new String("defaultUserId");
	if ( this.logger != null ) {
	    this.logger.debug( "bulkio::OutPort CTOR port: " + portName ); 
            this.streamContainer.setLogger(logger);
	}
    }

    public void setLogger( Logger newlogger ){
        synchronized (this.updatingPortsLock) {
	    logger = newlogger;
            this.streamContainer.setLogger(logger);
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
    public HashMap<String, BULKIO.dataVITA49Operations> getPorts() {
        return new HashMap<String, dataVITA49Operations>();
    }
 
    /**
     * pushSRI
     *     description: send out SRI describing the data payload
     *
     *  H: structure of type BULKIO::StreamSRI with the SRI for this stream
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
     *
     *  T: structure of type BULKIO::PrecisionUTCTime with the Time for this stream
     *    tcmode: timecode mode
     *    tcstatus: timecode status
     *    toff: Fractional sample offset
     *    twsec
     *    tfsec
     * @generated
     */
    public void pushSRI(StreamSRI header, PrecisionUTCTime time) 
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
            this.currentSRIs.put(header.streamID, new SriMapStruct(header, time));
            this.streamContainer.updateStreamSRIAndTime(header.streamID, header, time);
            if (this.active) {
                // state if this port is not listed in the filter table... then pushSRI down stream
                boolean portListed = false;

                // for each connection
                for (Entry<String, dataVITA49Operations> p : this.outConnections.entrySet()) {

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
                                p.getValue().pushSRI(header, time);
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
                    for (Entry<String, dataVITA49Operations> p : this.outConnections.entrySet()) {
                        try {
                            if ( logger != null ) {
                                logger.trace( "pushSRI - NO Filter port:" + this.name + " connection:" + p.getKey() +
                                              " streamID:" + header.streamID );
                            }
                            p.getValue().pushSRI(header, time);
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

        //1. loop over fitlerTable
        //   A. ignore other port names
        //   B. create mapping of streamid->connections(attachments)
        //
        boolean hasPortEntry = false;
        Map<String, Boolean> streamsFound = new HashMap<String, Boolean>();
        Map<String, ArrayList<VITA49StreamAttachment>> streamAttMap = new HashMap<String, ArrayList<VITA49StreamAttachment>>();
        ArrayList<VITA49StreamAttachment> list = new ArrayList<VITA49StreamAttachment>();

        // Populate streamsFound
        List<String> streamIds = new ArrayList<String>(Arrays.asList(this.streamContainer.getStreamIds()));
        for (String s: streamIds){
            streamsFound.put(s,Boolean.FALSE);
        }

        // Iterate through each filterTable entry and capture state
        for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {

            if (!ftPtr.port_name.getValue().equals(this.name)) {
                continue;
            }

            hasPortEntry = true;
            dataVITA49Operations connectedPort = this.outConnections.get(ftPtr.connection_id.getValue());
            if (connectedPort == null){
	        if ( logger != null ) {
	            logger.debug("bulkio.OutPort updateConnectionFilter() did not find connected port for connection_id " + ftPtr.connection_id +")" );
	        }
                continue;
            }
            
            // Keep track of which attachments are supposed to exist
            if (this.streamContainer.hasStreamId(ftPtr.stream_id.getValue())){
                streamsFound.put(ftPtr.stream_id.getValue(),Boolean.TRUE);
                VITA49StreamAttachment expectedAttachment = new VITA49StreamAttachment(ftPtr.connection_id.getValue(),connectedPort);
                ArrayList<VITA49StreamAttachment> streamAttList;
                if (streamAttMap.get(ftPtr.stream_id.getValue()) == null){
                    streamAttList = new ArrayList<VITA49StreamAttachment>();
                }else{
                    streamAttList = streamAttMap.get(ftPtr.stream_id.getValue());
                }
                streamAttList.add(expectedAttachment);
                streamAttMap.put(ftPtr.stream_id.getValue(), streamAttList);
            }
        }

        // Iterate through all attachment associations definted by filterEntries
        for(Map.Entry<String,ArrayList<VITA49StreamAttachment>> entry: streamAttMap.entrySet()){
            String streamId = entry.getKey();
            VITA49Stream foundStream = this.streamContainer.findByStreamId(streamId);
            if (foundStream != null){
                try{
                    foundStream.updateAttachments(entry.getValue().toArray(new VITA49StreamAttachment[0]));
                }catch (AttachError e){
	            if ( logger != null ) {
                        logger.error("bulkio::OutPort updateConnectionFilter() AttachError on updateAttachments() for streamId " + streamId);
                    }
                }catch (DetachError e){
	            if ( logger != null ) {
                        logger.error("bulkio::OutPort updateConnectionFilter() DetachError on updateAttachments() for streamId " + streamId);
                    }
                }catch (StreamInputError e){
                    if ( logger != null ) {
                        logger.error("bulkio::OutPort updateConnectionFilter() StreamInputError on updateAttachments() for streamId " + streamId);
		    }
                }
            }
        }

        if (hasPortEntry){
            // If there's a valid port entry, we need to detach unmentioned streams
            for(Map.Entry<String,Boolean> entry: streamsFound.entrySet()){
                if (!entry.getValue().booleanValue()){
                    VITA49Stream stream = this.streamContainer.findByStreamId(entry.getKey());
                    if(stream != null){
                        try{
                            stream.detachAll();
	                    if ( logger != null ) {
                                logger.debug("bulkio::OutPort updateConnectionFilter() calling detachAll() for streamId " + entry.getKey());
                            }
                        }catch (DetachError e){
	                    if ( logger != null ) {
                                logger.error("bulkio::OutPort updateConnectionFilter() DetachError on detachAll() for streamId " + entry.getKey());
                            }
                        }catch (StreamInputError e){
                            if ( logger != null ) {
                                logger.error("bulkio::OutPort updateConnectionFilter() StreamInputError on detachAll() for streamId " + entry.getKey());
		            }
                        }
                    }
                }
            }
        } else{
            // No port entry = All connections on
            for (Entry<String, dataVITA49Operations> p : this.outConnections.entrySet()) {
                try{
                    this.streamContainer.addConnectionToAllStreams(p.getKey(),p.getValue());
	            if ( logger != null ) {
                        logger.debug("bulkio::OutPort updateConnectionFilter() calling addConnectionToAllStreams for connection " + p.getKey());
                    }
                }catch (AttachError e){
	            if ( logger != null ) {
                        logger.error("bulkio::OutPort updateConnectionFilter() AttachError on updateAttachments() for all streams");
                    }
                }catch (DetachError e){
	            if ( logger != null ) {
                        logger.error("bulkio::OutPort updateConnectionFilter() DetachError on updateAttachments() for all streams");
                    }
                }catch (StreamInputError e){
                    if ( logger != null ) {
                        logger.error("bulkio::OutPort updateConnectionFilter() StreamInputError on updateAttachments() for all streams");
		    }
                }
            }
        }
        this.updateSRIForAllConnections();
        this.streamContainer.printState("After Filter Table Update");
    }

    public void  updateSRIForAllConnections() {
        // Iterate through stream objects in container
        //   Check if currentSRI has stream entry
        //     Yes: Check that ALL connections are listed in currentSRI entry
        //          Update currentSRI
        //     No:  PushSRI on all attachment ports
        //          Update currentSRI

        // Initialize variables
        String streamId;
        Set<String> streamConnIds = new HashSet<String>(); 
        Set<String> currentSRIConnIds = new HashSet<String>();
        Iterator connIdIter;

        // Iterate through all registered streams
        for (VITA49Stream s: this.streamContainer.getStreams()){
            streamId = s.getStreamId();
            streamConnIds = s.getConnectionIds();

            // Check if currentSRIs has entry for StreamId
            if (this.currentSRIs.containsKey(streamId)){
                SriMapStruct sriMap = this.currentSRIs.get(streamId);

                // Check if all connections on the streams have pushed SRI
                currentSRIConnIds = sriMap.connections;
                for (String connId:streamConnIds) {

                    // If not found, pushSRI and update currentSRIs container
                    if (!currentSRIConnIds.contains(connId)) {

                        // Grab the port
                        dataVITA49Operations connectedPort = this.outConnections.get(connId);
                        if (connectedPort == null) {
		            if ( logger != null ) {
                                logger.debug("updateSRIForAllConnections() Unable to find connected port with connectionId: " + connId);
                            }
                            continue;
                        }
                        // Push sri and update sriMap 
                        connectedPort.pushSRI(sriMap.sri, sriMap.time);
                        sriMap.connections.add(connId);
                    }
                }
            }
        }
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
            final dataVITA49Operations port;
            try {
                port = BULKIO.jni.dataVITA49Helper.narrow(connection);
            } catch (final Exception ex) {
		if ( logger != null ) {
		    logger.error("bulkio::OutPort CONNECT PORT: " + name + " PORT NARROW FAILED");
		}
                throw new CF.PortPackage.InvalidPort((short)1, "Invalid port for connection '" + connectionId + "'");
            }
            this.outConnections.put(connectionId, port);
            this.active = true;
            this.stats.put(connectionId, new linkStatistics( this.name, new Int8Size() ) );

            boolean portListed = false;

            for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {

                if (!ftPtr.port_name.getValue().equals(this.name)) {
                    continue;
                }
                portListed = true;
                if (ftPtr.connection_id.getValue().equals(connectionId)) {
                    try{
                        this.streamContainer.addConnectionToStream(connectionId, port,ftPtr.stream_id.getValue());
                    }catch (AttachError e){
		        if ( logger != null ) {
		            logger.error("bulkio::OutPort CONNECT PORT: " + name + " addConnectionToStream() AttachError for connectionId " + connectionId);
		        }
                    }catch (DetachError e){
		        if ( logger != null ) {
		            logger.error("bulkio::OutPort CONNECT PORT: " + name + " addConnectionToStream() DetachError for connectionId " + connectionId);
		        }
                    }catch (StreamInputError e){
		        if ( logger != null ) {
		            logger.error("bulkio::OutPort CONNECT PORT: " + name + " addConnectionToStream() StreamInputError for connectionId " + connectionId);
		        }
                    }
                }
            }
            if (!portListed ){
                try{
                    this.streamContainer.addConnectionToAllStreams(connectionId,port);
                }catch (AttachError e){
	            if ( logger != null ) {
		        logger.error("bulkio::OutPort CONNECT PORT: " + name + " addConnectionToAllStreams() AttachError for connectionId " + connectionId);
		    }
                }catch (DetachError e){
		    if ( logger != null ) {
		        logger.error("bulkio::OutPort CONNECT PORT: " + name + " addConnectionToAllStreams() DetachError for connectionId " + connectionId);
	            }
                }catch (StreamInputError e){
	            if ( logger != null ) {
	                logger.error("bulkio::OutPort CONNECT PORT: " + name + " addConnectionToAllStreams() StreamInputError for connectionId " + connectionId);
	            }
                }
            }

	    if ( logger != null ) {
		logger.debug("bulkio::OutPort CONNECT PORT: " + name + " CONNECTION '" + connectionId + "'");
	    }
        }
        this.streamContainer.printState("After connectPort");

    }

    /**
     * @generated
     */
    public void disconnectPort(String connectionId) {

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort disconnectPort ENTER (port=" + name +")" );
	}
        synchronized (this.updatingPortsLock) {
            try {
                this.streamContainer.detachByConnectionId(connectionId);
            } catch (DetachError e) {
                // PASS
            } catch (StreamInputError e) {
                // PASS
            }

            this.outConnections.remove(connectionId);
            this.stats.remove(connectionId);
            this.active = (this.outConnections.size() != 0);

	    if ( logger != null ) {
		logger.trace("bulkio.OutPort DISCONNECT PORT:" + name + " CONNECTION '" + connectionId + "'");
	    }

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

    /**
     * @generated
     */
    public UsesConnection[] connections() {
        final UsesConnection[] connList = new UsesConnection[this.outConnections.size()];
        int i = 0;
        synchronized (this.updatingPortsLock) {
            for (Entry<String, dataVITA49Operations> ent : this.outConnections.entrySet()) {
                connList[i++] = new UsesConnection(ent.getKey(), (org.omg.CORBA.Object) ent.getValue());
            }
        }
        return connList;
    }

    /**
     * @generated
     */
    public BULKIO.VITA49StreamDefinition[] getStreamDefinition(final String attachId)
    {
        ArrayList<VITA49Stream> streamList  = new ArrayList<VITA49Stream>(Arrays.asList(this.streamContainer.findByAttachId(attachId)));
        ArrayList<BULKIO.VITA49StreamDefinition> streamDefList  = new ArrayList<BULKIO.VITA49StreamDefinition>();
        for (VITA49Stream s: streamList){
            streamDefList.add(s.getStreamDefinition());
        }
        return streamDefList.toArray(new BULKIO.VITA49StreamDefinition[0]); 
    }

    /**
     * @generated
     */
    public String[] getUser(final String attachId)
    {
        ArrayList<VITA49Stream> streamList  = new ArrayList<VITA49Stream>(Arrays.asList(this.streamContainer.findByAttachId(attachId)));
        ArrayList<String> nameList  = new ArrayList<String>();
        for (VITA49Stream s: streamList){
            nameList.add(s.getName());
        }
        return nameList.toArray(new String[0]); 
    }

    /**
     * @generated
     */
    public InputUsageState usageState()
    {
        if (this.attachedStreams().length == 0) {
            return InputUsageState.IDLE;
        } else {
            return InputUsageState.ACTIVE;
        }
    }

    /**
     * @generated
     */
    public BULKIO.VITA49StreamDefinition[] attachedStreams()
    {
        return this.streamContainer.getCurrentStreamDefinitions(); 
    }

    /**
     * @generated
     */
    public String[] attachmentIds()
    {
        ArrayList<String> attachIdList  = new ArrayList<String>();
        for (VITA49Stream s: this.streamContainer.getStreams()){
            attachIdList.addAll(Arrays.asList(s.getAttachIds()));           
        }
        return attachIdList.toArray(new String[0]);
    }

    /**
     * @generated
     */
    public String[] attachmentIds(String streamId)
    {
        ArrayList<String> attachIdList  = new ArrayList<String>();
        for (VITA49Stream s: this.streamContainer.getStreams()){
            if(s.getStreamId().equals(streamId)){
                attachIdList.addAll(Arrays.asList(s.getAttachIds()));
            }
        }
        return attachIdList.toArray(new String[0]);
    }

    /**
     * @generated
     */
    public String[] attach(final BULKIO.VITA49StreamDefinition streamDef, final String userId) throws AttachError, StreamInputError, DetachError
    {
        // Eventually deprecate this method
        this.userId = userId;
        addStream(streamDef);
        String[] emptyArray = {};
        return emptyArray;
    }

    /**
     * @generated
     */
    public boolean updateStream(final BULKIO.VITA49StreamDefinition streamDef) throws AttachError, StreamInputError, DetachError
    {
        synchronized (this.updatingPortsLock) {
            if (!this.streamContainer.hasStreamId(streamDef.id)){
                return false;
            }
            this.streamContainer.removeStreamByStreamId(streamDef.id);
            return this.addStream(streamDef);
        }
    }

    /**
     * @generated
     */
    public boolean addStream(final BULKIO.VITA49StreamDefinition streamDef) throws AttachError, StreamInputError, DetachError
    {
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort addStream ENTER (port=" + name +")" );
	}

        String attachId = null;
        VITA49Stream stream;
        synchronized (this.updatingPortsLock) {
            stream = this.streamContainer.findByStreamId(streamDef.id);
            if (stream != null){
                //if stream already exists return false
                return false;
            }else{
                stream = new VITA49Stream(streamDef, this.userId, streamDef.id, null, null, null);
                this.streamContainer.addStream(stream);
            }

            boolean portListed = false;
            // for each connection
            for (Entry<String, dataVITA49Operations> p : this.outConnections.entrySet()) {
                // if connection is in the filter table
                for (connection_descriptor_struct ftPtr : bulkio.utils.emptyIfNull(this.filterTable) ) {
                    // if there is an entry for this port in the filter table....so save that state
                    if (ftPtr.port_name.getValue().equals(this.name)) {
                        portListed = true;
                    }
                    if ( (ftPtr.port_name.getValue().equals(this.name)) &&
                         (ftPtr.connection_id.getValue().equals(p.getKey())) &&
                         (ftPtr.stream_id.getValue().equals(streamDef.id))) {

                        if (this.currentSRIs.containsKey(stream.getStreamId())){
                            SriMapStruct sriMap = this.currentSRIs.get(stream.getStreamId());
                            stream.setSRI(sriMap.sri);
                            stream.setTime(sriMap.time);
                        }
                        stream.createNewAttachment(p.getKey(), p.getValue());
                    } 
                }
            }
            if (!portListed ) {
                if (this.currentSRIs.containsKey(stream.getStreamId())){
                    SriMapStruct sriMap = this.currentSRIs.get(stream.getStreamId());
                    stream.setSRI(sriMap.sri);
                    stream.setTime(sriMap.time);
                }
                for (Entry<String, dataVITA49Operations> p : this.outConnections.entrySet()) {
                    stream.createNewAttachment(p.getKey(),p.getValue());
                }
            }
        }

        String[] attachIds = stream.getAttachIds();
	if ( logger != null ) {
            for(String str: attachIds){ 
	        logger.trace("VITA49 PORT: addStream() ATTACHMENT COMPLETED ATTACH ID:" + str + " NAME(userid):" + stream.getName() );
            }
	    logger.trace("bulkio.OutPort addStream() EXIT (port=" + name +")" );
	}
        this.streamContainer.printState("After addStream");
        return true;
    }

    /**
     * @generated
     */
    public void removeStream(String streamId) throws AttachError, StreamInputError, DetachError
    {
        this.streamContainer.removeStreamByStreamId(streamId);
        this.streamContainer.printState("After removeStream");
    }

    /**
     * @generated
     */
    public void detach(String attachId, String connectionId) throws DetachError, StreamInputError
    {
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort detach ENTER (port=" + name +")" );
	}

        synchronized (this.updatingPortsLock) {
            if (attachId != null && connectionId != null){
                this.streamContainer.detachByAttachIdConnectionId(attachId,connectionId);
            }else if (attachId == null && connectionId != null){
                this.streamContainer.detachByConnectionId(connectionId);
            }else if(connectionId == null && attachId != null){
                this.streamContainer.detachByAttachId(attachId);
            }else{
                // detach all attachments on all streams
                this.streamContainer.detach();
            }
        }
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort detach ENTER (port=" + name +")" );
	}
        this.streamContainer.printState("After detach");
    }

    public void detach(String attachId) throws DetachError, StreamInputError
    {
	if ( logger != null ) {
	    logger.trace("bulkio.OutPort detach ENTER (port=" + name +")" );
	}

        if(attachId != null){
            this.streamContainer.detachByAttachId(attachId);
        }

	if ( logger != null ) {
	    logger.trace("bulkio.OutPort detach ENTER (port=" + name +")" );
	}
        this.streamContainer.printState("After detach");
    }


    /**
     * @generated
     */
    public class streamdefUseridPair {
        /** @generated */
        BULKIO.VITA49StreamDefinition streamDef;
        /** @generated */
        String userId;
        
        /** 
         * @generated
         */
        public streamdefUseridPair(final BULKIO.VITA49StreamDefinition streamDef, final String userId) {
            this.streamDef = streamDef;
            this.userId = userId;
        }
    }

}
