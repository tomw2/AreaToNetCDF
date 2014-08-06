
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.String;
import edu.wisc.ssec.mcidas.AncillaryData;

/**
 * NavigatorLambertConformal provides the ability to navigate
 * imagery in the Lambert Conformal projection.  This class
 * is essentially a math copy of the McIDAS source module
 * nvxlamb.dlm.
 *
 * @version 1.3 6 Aug 1999
 * @author Tommy Jasmin, SSEC
 */

class NavigatorLambertConformal implements Navigator {

  private static final float rad = 0.01745329f;
  private float row;
  private float col;
  private float r;
  private float space;
  private float qLon;
  private float pole;
  private float lat1;
  private float lat2;
  private float centralLat;
  private float centralLon;
  private static float fac;
  private float fac1;
  private float bLat;
  private int iPole;
  private static int iHem;
  private boolean isEastPositive=true;
 
  /**
   *
   * constructor
   *
   * @param ad          AncillaryData object
   * @param navBlock    navigation parameters array
   *
   */

  public NavigatorLambertConformal (
    AncillaryData ad, 
    int [] navBlock
  ) 
    throws IOException, NavigatorLCException

  {

    // store central lat and lon
    centralLat = ((float) navBlock[3])/10000.f;
    centralLon = ((float) navBlock[6])/10000.f;
    centralLon = -centralLon;

    row = (float) navBlock[1];
    col = (float) navBlock[2];
    iPole = navBlock[11];

    if (iPole == 0) {
      iPole = 900000;
    }

    iHem = 1;
    if (iPole < 0) {
      iHem = -1;
    }

    pole = ConversionUtility.FloatLatLon(iPole);
    lat1 = ConversionUtility.FloatLatLon(navBlock[3]);
    lat2 = ConversionUtility.FloatLatLon(navBlock[4]);

    lat1 = (90.0f - (iHem * lat1)) * rad;
    lat2 = (90.0f - (iHem * lat2)) * rad;

    space = navBlock[5] / 1000.0f;
    qLon = ConversionUtility.FloatLatLon(navBlock[6]);
    r = navBlock[7] / 1000.0f;

    fac = (float) ((Math.log(Math.sin(lat1)) - Math.log(Math.sin(lat2))) /
          (Math.log(Math.tan(0.5 * lat1)) - Math.log(Math.tan(0.5 * lat2))));
    fac1 = 1.0f / fac;
    bLat = (float) (r * Math.sin(lat1) / 
      (space * fac * Math.pow(Math.tan(lat1 * 0.5), fac)));

  }

  /**
   *
   * return central latitude
   *
   */
 
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

    double xLat = 0.0d;
    double xLon = 0.0d;
    double rLat = 0.0d;
    double rLon = 0.0d;
    double cLat = 0.0d;
    int count = latLon[0].length;
    double [][] linEle = new double[2][count];

    for (int i = 0; i < count; i++) {
      xLon = latLon[1][i];
      if (isEastPositive) xLon = -xLon;

      rLon = iHem * (xLon - qLon);
      rLon = ((rLon + 900.0d) % 360.0d) - 180.0d;
      rLon= rLon * fac * rad;
     
      cLat = (90.0d - (iHem * latLon[0][i])) * rad * 0.5d;
      if (cLat == 0.0d) {
        rLat = 0.0d;
      } else {
        rLat = bLat * Math.pow(Math.tan(Math.abs(cLat)), fac);
      }

      linEle[0][i] = row + iHem * (rLat * Math.cos(rLon));
      linEle[1][i] = col - iHem * (rLat * Math.sin(rLon));

    }

    return (linEle);

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

    double lineDiff = 0.0d;
    double elemDiff = 0.0d;
    double radius = 0.0d;
    double rLon = 0.0d;
    double xLat;
    double xLon;
    int count = linEle[0].length;
    double [][] latLon = new double[2][count];

    for (int i = 0; i < count; i++) {

      lineDiff = iHem * (linEle[0][0] - row) / bLat;
      elemDiff = -iHem * (linEle[1][0] - col) / bLat;

      rLon = 0.0d;
      if ((lineDiff != 0.0d) || (elemDiff != 0.0d)) {
        rLon = Math.atan2(elemDiff, lineDiff);
      }

      xLon = iHem * rLon / fac / rad + qLon;
      xLon = ((xLon + 900.0d) % 360.0d) - 180.0d;
      if (isEastPositive) xLon = -xLon;

      radius = Math.sqrt((lineDiff * lineDiff) + (elemDiff * elemDiff));

      if (Math.abs(radius) < 1.0E-10) {
        xLat = iHem * 90.0d;
      } else {
        xLat = (iHem * (90.0d - 2 * Math.atan(Math.exp(
          Math.log(radius) / fac)) / rad));
      }

      latLon[0][0] = xLat;
      latLon[1][0] = xLon;

    }

    return (latLon);

  }

}
