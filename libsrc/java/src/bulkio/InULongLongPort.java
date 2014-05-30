package bulkio;
import org.apache.log4j.Logger;

/**
 * 
 */
public class InULongLongPort extends InUInt64Port {
    
    /**
     * 
     */
    public InULongLongPort( String portName ) {
	super( portName );
    }

    public InULongLongPort( String portName, 
		       bulkio.sri.Comparator compareSRI ) {
	super( portName, null, compareSRI, null );
    }

    public InULongLongPort( String portName, 
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener sriCallback ) {
	super( portName, null, compareSRI, sriCallback);
    }

    public InULongLongPort( String portName, Logger logger ) {
	super( portName, logger );
    }

    public InULongLongPort( String portName, 
		       Logger logger,
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener sriCallback ) {
	super( portName, logger, compareSRI, sriCallback);
    }

}

