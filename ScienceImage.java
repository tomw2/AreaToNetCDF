// following is for use of AddeURLConnection class.
import edu.wisc.ssec.mcidas.*;
import edu.wisc.ssec.mcidas.adde.*;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.String;
import java.net.URL;

/**
 * This class reads in the appropriate data from the input file
 * and creates navigation, calibration, ancillary data and sensor 
 * data objects to help manage the conversion to NetCDF format data.
 *
 * @version 1.7 6 Aug 1999
 * @author Tommy Jasmin
 */

class ScienceImage {

  // some McIDAS sensor numbers recognized so far
  private static final int GOES_8_IMAGER   = 70;
  private static final int GOES_9_IMAGER   = 72;
  private static final int GOES_10_IMAGER  = 74;
  private static final int GOES_11_IMAGER  = 76;
  private static final int GOES_12_IMAGER  = 78;
  private static final int GOES_13_IMAGER  = 180;

  private static final int GOES_8_SOUNDER  = 71;
  private static final int GOES_9_SOUNDER  = 73;
  private static final int GOES_10_SOUNDER = 75;
  private static final int GOES_11_SOUNDER = 77;
  private static final int GOES_12_SOUNDER = 79;
  private static final int GOES_13_SOUNDER = 181;

  private SensorData sd = null;
  private AncillaryData ad = null;
  private DataInputStream dis = null;
  private Navigator n = null;
  private Calibrator c = null;
  private int [] navBlock; 
  private int [] calBlock; 
  private static int intGVAR = 1196835154;
  private static int intLAMB = 1279348034;
  private static int intMERC = 1296388675;
  private static int intPS   = 1347624992;
  private static int intGEOS   = 1195724627;

  private int sensorId = -1;

  /**
   *
   * constructor
   *
   * @param fileName	input file name
   *
   */

  public ScienceImage(String fileName) 
    throws IOException

  {

    if ((fileName.indexOf("image") < 0) && 
        (fileName.indexOf("grid")  < 0) && 
        (fileName.indexOf("point") < 0)) {

      // assume this is a valid McIDAS area for now, should really check
      dis = new DataInputStream (
        new BufferedInputStream(new FileInputStream(fileName), 2048)
      );

    } else {

      // define a special adde stream handler factory
      URL.setURLStreamHandlerFactory(new AddeURLStreamHandlerFactory());

      AddeURLConnection urlc = null;
   
      try {
        URL url = new URL(fileName);
        urlc = (AddeURLConnection) url.openConnection();
      } catch (Exception e) {
        System.out.println("Error: " + e);
      }

      dis = urlc.getDataInputStream();

    }

    // first, create an ancillary data object
    ad = new AncillaryData(dis);

    // now create a navigator object if possible
    int navBytes = 0;
 
    // figure out how many nav bytes to read
    if (ad.getNavOffset() > 0) {
      if (ad.getCalOffset() > 0) {
        navBytes = ad.getCalOffset() - ad.getNavOffset();
      } else {
        navBytes = ad.getDataOffset() - ad.getNavOffset();
      }
    }
 
    // allocate space, then read the words into a McIDAS-like nav block
    if (navBytes > 0) {
      navBlock = new int[navBytes / 4];
      for (int i = 0; i < navBytes / 4; i++) {
        navBlock[i] = dis.readInt();
      }
      // see if we have to byte swap the nav block
      if (ad.isSwapped()) {
        if (navBlock[0] == intGVAR) {
          ConversionUtility.swap(navBlock, 2,   126);
          ConversionUtility.swap(navBlock, 129, 254);
          ConversionUtility.swap(navBlock, 257, 382);
          ConversionUtility.swap(navBlock, 385, 510);
          ConversionUtility.swap(navBlock, 513, 638);
        } else {
          ConversionUtility.swap(navBlock, 1, navBlock.length - 1);
        }
      }
    }

    // create nav objects for the projection types we know how to

    // GVAR
    if (navBlock[0] == intGVAR) {
      try {
        n = new NavigatorGvar(ad, navBlock);
        System.out.println("making Gvar nav object...");
      } catch (NavigatorGException e) {
        n = null;
        System.out.println(e);
      }
    }

    // GEOS
    if (navBlock[0] == intGEOS) {
      try {
        n = new NavigatorGeos(ad, navBlock);
        System.out.println("making Geos nav object...");
      } catch (NavigatorGException e) {
        n = null;
        System.out.println(e);
      }
    }

    // LAMB = Lambert Conformal
    if (navBlock[0] == intLAMB) {
      try {
        n = new NavigatorLambertConformal(ad, navBlock);
        System.out.println("making Lambert Conformal nav object...");
      } catch (NavigatorLCException e) {
        n = null;
        System.out.println(e);
      }
    }

    // MERC = Mercator
    if (navBlock[0] == intMERC) {
      try {
        n = new NavigatorMercator(ad, navBlock);
        System.out.println("making Mercator nav object...");
      } catch (NavigatorMException e) {
        n = null;
        System.out.println(e);
      }
    }

    // PS = Polar Stereographic
    if (navBlock[0] == intPS) {
      try {
        n = new NavigatorPolarStereographic(ad, navBlock);
        System.out.println("making Polar Stereographic nav object...");
      } catch (NavigatorPSException e) {
        n = null;
        System.out.println(e);
      }
    }

    // now create a calibrator object if possible
    int calBytes = 0;
 
    // figure out how many cal bytes to read
    if (ad.getCalOffset() > 0) {
      calBytes = ad.getDataOffset() - ad.getCalOffset();
    }

    if (calBytes > 0) {

      // allocate space, then read data into a McIDAS-like cal block
      calBlock = new int[calBytes / 4];
      for (int i = 0; i < calBytes / 4; i++) {
        calBlock[i] = dis.readInt();
      }

      // see if we have to byte swap the cal block
      if (ad.isSwapped()) {
        ConversionUtility.swap(calBlock, 1, calBlock.length - 1);
      }

      // create cal objects for the sensor types we know how to
      // note for now we only know how to calibrate GVAR
      sensorId = ad.getSensorId();


      switch (sensorId) {

        case GOES_8_IMAGER:
        case GOES_8_SOUNDER:
          System.out.println("making GOES 8 cal object...");
          c = new CalibratorGvarG8(dis, ad, calBlock);
          break;

        case GOES_9_IMAGER:
        case GOES_9_SOUNDER:
          System.out.println("making GOES 9 cal object...");
          c = new CalibratorGvarG9(dis, ad, calBlock);
          break;

        case GOES_10_IMAGER:
        case GOES_10_SOUNDER:
          System.out.println("making GOES 10 cal object...");
          c = new CalibratorGvarG10(dis, ad, calBlock);
          break;

        case GOES_12_IMAGER:
        case GOES_12_SOUNDER:
          System.out.println("making GOES 12 calibration object...");
          c = new CalibratorGvarG12(dis, ad, calBlock);
          break;

        case GOES_13_IMAGER:
        case GOES_13_SOUNDER:
          System.out.println("making GOES 13 calibration object...");
          c = new CalibratorGvarG13(dis, ad, calBlock);
          break;


        default:
          System.out.println("making default cal object...ID="+sensorId);
          c = new CalibratorDefault(dis, ad);
          break;

      }

    } else {

      // create a default calibrator if no cal data 
      System.out.println("no cal block: making default cal object...");
      c = new CalibratorDefault(dis, ad);

    }

    // finally, create the sensor data object
    sd = new SensorData(dis, ad);

  }

  /**
   *
   * return reference to AncillaryData object
   *
   */

  public AncillaryData getAD() {
    return ad;
  }

  /**
   *
   * return reference to Navigator object
   *
   */

  public Navigator getN() {
    return n;
  }

  /**
   *
   * return reference to Calibrator object
   *
   */

  public Calibrator getC() {
    return c;
  }

  /**
   *
   * return reference to SensorData object
   *
   */

  public SensorData getSD() {
    return sd;
  }

}
