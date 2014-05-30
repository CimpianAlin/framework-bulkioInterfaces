package bulkio;
import org.apache.log4j.Logger;
/**
 * 
 */
public class InCharPort extends InInt8Port {
    
    /**
     * 
     */
    public InCharPort( String portName ) {
	super( portName );
    }

    public InCharPort( String portName, 
		       bulkio.sri.Comparator compareSRI ) {
	super( portName, null, compareSRI, null );
    }

    public InCharPort( String portName, 
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener sriCallback ) {
	super( portName, null, compareSRI, sriCallback);
    }

    public InCharPort( String portName, Logger logger ) {
	super( portName, logger );
    }

    public InCharPort( String portName, 
		       Logger logger,
		       bulkio.sri.Comparator compareSRI, 
		       bulkio.SriListener sriCallback ) {
	super( portName, logger, compareSRI, sriCallback);
    }


}

