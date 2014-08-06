
import java.io.DataInputStream;
import java.io.IOException;
import edu.wisc.ssec.mcidas.GVARnav;
import edu.wisc.ssec.mcidas.AncillaryData;

/**
 * NavigatorGvar provides the ability to navigate imagery in the
 * GVAR projection.  This method pretty much wraps GVARnav, which
 * is essentially a math copy of the McIDAS source module
 * nvxgvar.dlm.
 *
 * @version 1.6 6 Aug 1999
 * @author Tommy Jasmin, SSEC
 */

class NavigatorGvar implements Navigator {

  private GVARnav ng = null;
 
  /**
   *
   * constructor
   *
   * @param ad          AncillaryData object
   * @param navBlock    navigation parameters array
   *
   */

  public NavigatorGvar (
    AncillaryData ad, 
    int [] navBlock
  ) 
    throws IOException, NavigatorGException

  {
    // create a GVARnav object - this will do all the work
    ng = new GVARnav(1, navBlock);

    // set base res multiplier to that specified in AncillaryData object
    ng.setRes(ad.getLineRes(), ad.getElemRes());
    // same for image starting coordinates and source coordinates
    ng.setImageStart(ad.getStartLine(), ad.getStartElem());
    ng.setStart(1,1);
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

}
