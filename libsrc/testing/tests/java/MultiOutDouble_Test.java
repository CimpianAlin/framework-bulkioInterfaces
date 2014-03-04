
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Layout;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.xml.DOMConfigurator;
import BULKIO.StreamSRI;
import BULKIO.PrecisionUTCTime;
import BULKIO.PortStatistics;
import BULKIO.PortUsageType;
import BULKIO.dataSDDSPackage.AttachError;
import BULKIO.dataSDDSPackage.DetachError;
import BULKIO.dataSDDSPackage.StreamInputError;
import org.omg.CORBA.ORB;

/**
 * Tests for {@link Foo}.
 *
 * @author 
 */
@RunWith(JUnit4.class)
public class MultiOutDouble_Test {

    Logger logger =  Logger.getRootLogger();

    public static ORB orb;

    bulkio.InDoublePort ip1 = null;
    bulkio.InDoublePort ip2 = null;
    bulkio.InDoublePort ip3 = null;
    bulkio.InDoublePort ip4 = null;
    bulkio.OutDoublePort port = null;

    protected List< bulkio.connection_descriptor_struct> filterTable = null;

    @BeforeClass
	public static void oneTimeSetUp() {
	// Set up a simple configuration that logs on the console.
	BasicConfigurator.configure();

	// Create and initialize the ORB
	String [] args = new String[0];
	orb = ORB.init(args, null);
    }

    @AfterClass
	public static void oneTimeTearDown() {

    }

    @Before
	public void setUp() {
	ip1 = new bulkio.InDoublePort("sink_1",logger);
	ip2 = new bulkio.InDoublePort("sink_2", logger );
	ip3 = new bulkio.InDoublePort("sink_3", logger );
	ip4 = new bulkio.InDoublePort("sink_4", logger );
	port = new bulkio.OutDoublePort("multiout_source", logger );

	filterTable = new ArrayList< bulkio.connection_descriptor_struct>(10);
	filterTable.add( new bulkio.connection_descriptor_struct("connection_1", "stream-1-1", "multiout_source") );
	filterTable.add( new bulkio.connection_descriptor_struct("connection_1", "stream-1-2", "multiout_source") );
	filterTable.add( new bulkio.connection_descriptor_struct("connection_1", "stream-1-3", "multiout_source") );

	filterTable.add( new bulkio.connection_descriptor_struct("connection_2", "stream-2-1", "multiout_source") );
	filterTable.add( new bulkio.connection_descriptor_struct("connection_2", "stream-2-2", "multiout_source") );
	filterTable.add( new bulkio.connection_descriptor_struct("connection_2", "stream-2-3", "multiout_source") );

	filterTable.add( new bulkio.connection_descriptor_struct("connection_3", "stream-3-1", "multiout_source") );
	filterTable.add( new bulkio.connection_descriptor_struct("connection_3", "stream-3-2", "multiout_source") );
	filterTable.add( new bulkio.connection_descriptor_struct("connection_3", "stream-3-3", "multiout_source") );

	filterTable.add( new bulkio.connection_descriptor_struct("connection_4", "stream-4-1", "multiout_source") );

    }

    @After
	public void tearDown() {
    }

    @Test
	public void test_multiout_sri_filtered( ) {

	logger.info("------ Testing Multiout SRI Filtered BEGIN -----");

	try {
	    port.connectPort( ip1._this_object(orb), "connection_1");
	    port.connectPort( ip2._this_object(orb), "connection_2");
	    port.connectPort( ip3._this_object(orb), "connection_3");
	    port.connectPort( ip4._this_object(orb), "connection_4");
	}
	catch( Exception e  ) {
	}
	port.updateConnectionFilter( filterTable );

	String filter_stream_id = new String("stream-1-1");
	double srate=11.0;
	double xdelta = 1.0/srate;
	short xunits=1;
	BULKIO.StreamSRI sri;
	BULKIO.StreamSRI []streams;
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );

	port.pushSRI( sri );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==0 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==0 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==0 );

	//
	// Reset connection table
	//
	port.updateConnectionFilter( null );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==1 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==1 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==1 );
    }

    @Test
	public void test_multiout_sri_eos_filtered( ) {

	logger.info("------ Testing Multiout SRI/EOS Filtered BEGIN -----");

	try {
	    port.connectPort( ip1._this_object(orb), "connection_1");
	    port.connectPort( ip2._this_object(orb), "connection_2");
	    port.connectPort( ip3._this_object(orb), "connection_3");
	    port.connectPort( ip4._this_object(orb), "connection_4");
	}
	catch( Exception e  ) {
	}
	port.updateConnectionFilter( filterTable );

	String filter_stream_id = new String("stream-1-1");
	double srate=11.0;
	double xdelta = 1.0/srate;
	short xunits=1;
	BULKIO.PrecisionUTCTime TS = bulkio.time.utils.now();
	BULKIO.StreamSRI sri;
	BULKIO.StreamSRI asri;
	BULKIO.StreamSRI []streams;

	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 1 StreamID Mismatch",   filter_stream_id.equals(asri.streamID) );
	assertTrue("Stream SRI - 1 Mode Mismatch",   asri.mode == 0 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==0 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==0 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==0 );


	//
	// Push SRI for IP2
	//
	filter_stream_id =  "stream-2-1";
	srate=22.0;
	xdelta = 1.0/srate;
	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );


	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 1 StreamID Mismatch",   "stream-1-1".equals(asri.streamID) );
	assertTrue("Stream SRI - 1 Mode Mismatch",   asri.mode == 0 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 2 StreamID Mismatch",   filter_stream_id.equals(asri.streamID) );
	assertTrue("Stream SRI - 2 Mode Mismatch",   asri.mode == 0 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==0 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==0 );

	//
	// Push SRI for IP3
	//
	filter_stream_id =  "stream-3-1";
	srate=33.0;
	xdelta = 1.0/srate;
	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 1 StreamID Mismatch",   "stream-1-1".equals(asri.streamID) );
	assertTrue("Stream SRI - 1 Mode Mismatch",   asri.mode == 0 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 2 StreamID Mismatch",   "stream-2-1".equals(asri.streamID) );
	assertTrue("Stream SRI - 2 Mode Mismatch",   asri.mode == 0 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==1  );
	asri=streams[0];  
	assertTrue("Stream SRI - 3 StreamID Mismatch",   filter_stream_id.equals(asri.streamID) );
	assertTrue("Stream SRI - 3 Mode Mismatch",   asri.mode == 0 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==0 );

	//
	// Push SRI for IP4
	//
	filter_stream_id =  "stream-4-1";
	srate=44.0;
	xdelta = 1.0/srate;
	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 1 StreamID Mismatch",   "stream-1-1".equals(asri.streamID) );
	assertTrue("Stream SRI - 1 Mode Mismatch",   asri.mode == 0 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 2 StreamID Mismatch",   "stream-2-1".equals(asri.streamID) );
	assertTrue("Stream SRI - 2 Mode Mismatch",   asri.mode == 0 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==1  );
	asri=streams[0];  
	assertTrue("Stream SRI - 3 StreamID Mismatch",   "stream-3-1".equals(asri.streamID) );
	assertTrue("Stream SRI - 3 Mode Mismatch",   asri.mode == 0 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==1 );
	asri=streams[0];  
	assertTrue("Stream SRI - 3 StreamID Mismatch",   filter_stream_id.equals(asri.streamID) );
	assertTrue("Stream SRI - 3 Mode Mismatch",   asri.mode == 0 );

	//
	// Send EOS downstream and check activeSRIs
	//
	port.updateConnectionFilter( filterTable );

	filter_stream_id = "stream-1-1";
	double[] v = new double[0];
	port.pushPacket( v, TS, true, filter_stream_id );

	bulkio.InDoublePort.Packet pkt = ip1.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("GetPacket 1 Pkt was empty",  pkt != null );
	assertTrue("GetPacket 1 StreamID Mismatch",   pkt.SRI.streamID.equals(filter_stream_id) );
	assertTrue("GetPacket 1 Mode Mismatch",   pkt.SRI.mode == 0 );
	assertTrue("GetPacket 1 EOS Mismatch",   pkt.EOS == true );

	filter_stream_id = "stream-2-1";
	port.pushPacket( v, TS, true, filter_stream_id );
	pkt = ip2.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("GetPacket 2 Pkt was empty",  pkt != null );
	assertTrue("GetPacket 2 StreamID Mismatch",   pkt.SRI.streamID.equals(filter_stream_id) );
	assertTrue("GetPacket 2 Mode Mismatch",   pkt.SRI.mode == 0 );
	assertTrue("GetPacket 2 EOS Mismatch",   pkt.EOS == true );

	filter_stream_id = "stream-3-1";
	port.pushPacket( v, TS, true, filter_stream_id );
	pkt = ip3.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("GetPacket 2 Pkt was empty",  pkt != null );
	assertTrue("GetPacket 2 StreamID Mismatch",   pkt.SRI.streamID.equals(filter_stream_id) );
	assertTrue("GetPacket 2 Mode Mismatch",   pkt.SRI.mode == 0 );
	assertTrue("GetPacket 2 EOS Mismatch",   pkt.EOS == true );


	filter_stream_id = "stream-4-1";
	port.pushPacket( v, TS, true, filter_stream_id );
	pkt = ip4.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("GetPacket 2 Pkt was empty",  pkt != null );
	assertTrue("GetPacket 2 StreamID Mismatch",   pkt.SRI.streamID.equals(filter_stream_id) );
	assertTrue("GetPacket 2 Mode Mismatch",   pkt.SRI.mode == 0 );
	assertTrue("GetPacket 2 EOS Mismatch",   pkt.EOS == true );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==0 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==0 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==0 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==0 );

	//
	// Reset connection table
	//
	port.updateConnectionFilter( null );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );

	streams = ip1.activeSRIs();
	assertTrue("Stream SRI Sequence - 1 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 1 SRI, length",  streams.length ==1 );

	streams = ip2.activeSRIs();
	assertTrue("Stream SRI Sequence - 2 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 2 SRI, length",  streams.length ==1 );

	streams = ip3.activeSRIs();
	assertTrue("Stream SRI Sequence - 3 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 3 SRI, length",  streams.length ==1 );

	streams = ip4.activeSRIs();
	assertTrue("Stream SRI Sequence - 4 SRI",  streams != null );
	assertTrue("Stream SRI Sequence - 4 SRI, length",  streams.length ==1 );



    }


    @Test
	public void test_multiout_data_filtered( ) {

	logger.info("------ Testing Multiout DATA Filtered BEGIN -----");

	try {
	    port.connectPort( ip1._this_object(orb), "connection_1");
	    port.connectPort( ip2._this_object(orb), "connection_2");
	    port.connectPort( ip3._this_object(orb), "connection_3");
	    port.connectPort( ip4._this_object(orb), "connection_4");
	}
	catch( Exception e  ) {
	}
	port.updateConnectionFilter( filterTable );

	String filter_stream_id = new String("stream-1-1");
	double srate=11.0;
	double xdelta = 1.0/srate;
	short xunits=1;
	BULKIO.PrecisionUTCTime TS = bulkio.time.utils.now();
	BULKIO.StreamSRI sri;
	BULKIO.StreamSRI asri;
	BULKIO.StreamSRI []streams;
	double[] v = new double[91];
	bulkio.InDoublePort.Packet pkt;

	logger.info("------ MultiOut DATA Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );
	port.pushPacket( v, TS, false, filter_stream_id  );

	pkt = ip1.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, Pkt was empty:",  pkt != null );
	assertTrue("Get a Packet, StreamID mismatch:",  filter_stream_id.equals(pkt.SRI.streamID)  );
	assertTrue("Get a Packet, EOS mismatch:",  pkt.EOS == false );
	assertTrue("Get a Packet, mode mismatch:",  pkt.SRI.mode == 0  );
	assertTrue("Get a Packet, Data Length mismatch:",  pkt.dataBuffer.length == 91  );

	pkt = ip2.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP2 PKT was not EMPTY",  pkt == null );

	pkt = ip3.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP3 PKT was not EMPTY",  pkt == null );

	pkt = ip4.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP4 PKT was not EMPTY",  pkt == null );

	//
	// Push DATA for IP2
	//
	filter_stream_id =  "stream-2-1";
	srate=22.0;
	xdelta = 1.0/srate;
	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );
	port.pushPacket( v, TS, false, filter_stream_id  );

	pkt = ip1.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP1 PKT was not EMPTY",  pkt == null );

	pkt = ip2.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, Pkt was empty:",  pkt != null );
	assertTrue("Get a Packet, StreamID mismatch:",  filter_stream_id.equals(pkt.SRI.streamID)  );
	assertTrue("Get a Packet, EOS mismatch:",  pkt.EOS == false );
	assertTrue("Get a Packet, mode mismatch:",  pkt.SRI.mode == 0  );
	assertTrue("Get a Packet, Data Length mismatch:",  pkt.dataBuffer.length == 91  );

	pkt = ip3.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP3 PKT was not EMPTY",  pkt == null );

	pkt = ip4.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP4 PKT was not EMPTY",  pkt == null );


	//
	// Push DATA for IP3
	//
	filter_stream_id =  "stream-3-1";
	srate=33.0;
	xdelta = 1.0/srate;
	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );
	port.pushPacket( v, TS, false, filter_stream_id  );

	pkt = ip1.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP1 PKT was not EMPTY",  pkt == null );

	pkt = ip2.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP2 PKT was not EMPTY",  pkt == null );

	pkt = ip3.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, Pkt was empty:",  pkt != null );
	assertTrue("Get a Packet, StreamID mismatch:",  filter_stream_id.equals(pkt.SRI.streamID)  );
	assertTrue("Get a Packet, EOS mismatch:",  pkt.EOS == false );
	assertTrue("Get a Packet, mode mismatch:",  pkt.SRI.mode == 0  );
	assertTrue("Get a Packet, Data Length mismatch:",  pkt.dataBuffer.length == 91  );

	pkt = ip4.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP4 PKT was not EMPTY",  pkt == null );

	//
	// Push DATA for IP4
	//
	filter_stream_id =  "stream-4-1";
	srate=44.0;
	xdelta = 1.0/srate;
	logger.info("------ MultiOut SRI Filtered SID:" + filter_stream_id );
	sri = bulkio.sri.utils.create(filter_stream_id, srate, xunits, false );
	port.pushSRI( sri );
	port.pushPacket( v, TS, false, filter_stream_id  );

	pkt = ip1.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP1 PKT was not EMPTY",  pkt == null );

	pkt = ip2.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP2 PKT was not EMPTY",  pkt == null );

	pkt = ip3.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, IP3 PKT was not EMPTY",  pkt == null );

	pkt = ip4.getPacket( bulkio.Const.NON_BLOCKING );
	assertTrue("Get a Packet, Pkt was empty:",  pkt != null );
	assertTrue("Get a Packet, StreamID mismatch:",  filter_stream_id.equals(pkt.SRI.streamID)  );
	assertTrue("Get a Packet, EOS mismatch:",  pkt.EOS == false );
	assertTrue("Get a Packet, mode mismatch:",  pkt.SRI.mode == 0  );
	assertTrue("Get a Packet, Data Length mismatch:",  pkt.dataBuffer.length == 91  );

    }



}
