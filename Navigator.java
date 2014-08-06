/**
 * interface for creating Navigator classes.
 *
 * @version 1.2 6 Aug 1999
 * @author Tommy Jasmin, SSEC
 */

interface Navigator {

  public double [][] toLatLon (
    double [][] linEle
  );

  public double [][] toLinEle (
    double [][] latLon
  );

}
