import java.io.DataInputStream;
import java.io.IOException;
import java.lang.String;
import edu.wisc.ssec.mcidas.AncillaryData;
import edu.wisc.ssec.mcidas.PSnav;

/**
 * NavigatorPolarStereographic provides the ability to navigate
 * imagery in the Polar Stereographic projection.  This class
 * is essentially a math copy of the McIDAS source module
 * nvxps.dlm.
 *
 * @version 1.3 6 Aug 1999
 * @author Tommy Jasmin, SSEC
 */

class NavigatorPolarStereographic implements Navigator {
  private PSnav ng = null;

  private float centralLat;
  private float centralLon;
 
  /**
   *
   * constructor
   *
   * @param ad          AncillaryData object
   * @param navBlock    navigation parameters array
   *
   */

  public NavigatorPolarStereographic (
    AncillaryData ad, 
    int [] navBlock
  ) 
    throws IOException, NavigatorPSException

  {

    ng = new PSnav(navBlock);
    ng.setRes(ad.getLineRes(), ad.getElemRes());
    ng.setImageStart(ad.getStartLine(), ad.getStartElem());
    ng.setStart(1,1);

    // store central lat and lon
    centralLat = ((float) navBlock[3])/10000.f;
    centralLon = ((float) navBlock[5])/10000.f;
    centralLon = -centralLon;


  }

  /**
   *
   * return central latitude
   *
   */
 
  public float getCentralLat () {
    return (centralLat);
  }

  /**
   *
   * return central longitude
   *
   */
 
  public float getCentralLon () { 
    return (centralLon); 
  }

  /**
   *
   * given a set of lat/lon, return the corresponding line/element
   *
   * @param latLon      an array of latitudes and longitudes
   *
   */
 
  public double [][] toLinEle ( double [][] latLon) {
    return ng.toLinEle(latLon);
  }

  /**
   *
   * given a set of lines/elements, return the corresponding lats/lons.
   *
   * @param linEle      an array of lines and elements
   *
   */
 
  public double [][] toLatLon ( double [][] linEle) {
    return ng.toLatLon(linEle);
  }

}
