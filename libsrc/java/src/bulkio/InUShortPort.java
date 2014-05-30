package bulkio;
import org.apache.log4j.Logger;

/**
 * 
 */
public class InUShortPort extends InUInt16Port {
    
    /**
     * 
     */
    public InUShortPort( String portName ) {
	super( portName );
    }

    public InUShortPort( String portName, 
		       bulkio.sri.Comparator compareSRI ) {
	super( portName, null, compareSRI, null );
    }

    public InUShortPort( String portName, 
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener sriCallback ) {
	super( portName, null, compareSRI, sriCallback);
    }

    public InUShortPort( String portName, Logger logger ) {
	super( portName, logger );
    }

    public InUShortPort( String portName, 
		       Logger logger,
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener sriCallback ) {
	super( portName, logger, compareSRI, sriCallback);
    }

}

