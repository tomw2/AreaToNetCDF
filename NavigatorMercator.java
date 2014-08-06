import java.io.DataInputStream;
import java.io.IOException;
import edu.wisc.ssec.mcidas.MERCnav;
import edu.wisc.ssec.mcidas.AncillaryData;

/**
 * NavigatorMercator provides the ability to navigate imagery in the
 * Mercator projection.  This method pretty much wraps MERCnav, which
 * is essentially a math copy of the McIDAS source module
 * nvxmerc.dlm.
 *
 * @version 1.6 6 Aug 1999
 * @author Tommy Jasmin, SSEC
 */

class NavigatorMercator implements Navigator {

  private MERCnav ng = null;
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

  public NavigatorMercator (
    AncillaryData ad, 
    int [] navBlock
  ) 
    throws IOException, NavigatorMException

  {
    // create a MERCnav object - this will do all the work
    ng = new MERCnav(navBlock);

    // set base res multiplier to that specified in AncillaryData object
    ng.setRes(ad.getLineRes(), ad.getElemRes());
    // same for image starting coordinates and source coordinates
    ng.setImageStart(ad.getStartLine(), ad.getStartElem());
    ng.setStart(1,1);
    centralLat = ConversionUtility.FloatLatLon(navBlock[3]);
    centralLon = ConversionUtility.FloatLatLon(navBlock[5]);
    centralLon = -centralLon;
  }

  /**
   *
   * given a set of lat/lon, return the corresponding line/element
   *
   * @param latLon      an array of latitudes and longitudes
   *
   */

  public double [][] toLinEle (
    double [][] latLon
  )

  {
    return ng.toLinEle(latLon);
  }

  /**
   *
   * given a set of lines/elements, return the corresponding lats/lons.
   *
   * @param linEle      an array of lines and elements
   *
   */

  public double [][] toLatLon (
    double [][] linEle
  )

  {
    return ng.toLatLon(linEle);
  }

  public float getCentralLat ()

  {
    return (centralLat);
  }

  /**
   *
   * return central longitude
   *
   */

  public float getCentralLon ()

  {
    return (centralLon);
  }

}
