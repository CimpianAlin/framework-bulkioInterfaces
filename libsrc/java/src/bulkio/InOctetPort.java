package bulkio;
import org.apache.log4j.Logger;
/**
 * 
 */
public class InOctetPort extends InUInt8Port {

    public InOctetPort( String portName ) {
	super( portName );
    }

    public InOctetPort( String portName, 
			bulkio.sri.Comparator compareSRI ) {
	super( portName, null, compareSRI, null );
    }

    public InOctetPort( String portName, 
			bulkio.sri.Comparator compareSRI, 
			bulkio.SriListener sriCallback ) {
	super( portName, null, compareSRI, sriCallback);
    }

    public InOctetPort( String portName, Logger logger ) {
	super( portName, logger );
    }

    public InOctetPort( String portName, 
			Logger logger,
			bulkio.sri.Comparator compareSRI, 
			bulkio.SriListener sriCallback ) {
	super( portName, logger, compareSRI, sriCallback);
    }

}

