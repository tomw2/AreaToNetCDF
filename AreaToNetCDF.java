
import java.io.IOException;
import java.util.Date;
import ucar.multiarray.*;
import ucar.netcdf.*;
import ucar.netcdf.Dimension;
import java.*;
import java.awt.*;
import java.awt.image.*;
import java.lang.*;
import java.util.*;
import edu.wisc.ssec.mcidas.*;

/**
 * AreaToNetCDF converts image files in McIDAS area format to
 * NetCDF format.  There is at present a very loose definition
 * of exactly what should go into a NetCDF format image file,
 * but we are trying to refine that, and the current output has
 * been proven useful in IDL and AWIPS.
 *
 * Usage is shown by invoking the class with the -HELP option only.
 *
 * @version 1.6 6 Aug 1999
 * @version 1.7 24 Oct 2011
 * @author Tommy Jasmin, SSEC
 */

public class AreaToNetCDF extends Frame {

private static final String VERSION = "1.7";

  public static void main(String[] args) {
    AreaToNetCDF anc = new AreaToNetCDF(args);
    System.exit(0);
  }

  public AreaToNetCDF(String[] args) {

  ScienceImage si = null;
  AncillaryData ad = null;
  int navFlag = 1;
  int awipsFlag = 0;
  int gifFlag = 0;
  String GIFFilename = null;
  int charCount = 0;
  boolean isQuiet = false;
  String fInName;
  String fOutName;
  String statusMsg;
  int calTypeOut = Calibrator.CAL_RAD;
  float lat1, lon1, lat2, lon2;
  float xRes, yRes;
  float cLat = Float.NaN;
  float cLon = Float.NaN;
  boolean useCF = false;

  // get input and output file name

  if (args.length == 0 || args[0].indexOf("-HELP") >= 0 || args[0].indexOf("-?") >= 0) {
    System.out.println(
      "usage: jre -cp PATH/AreaToNetCDF.jar AreaToNetCDF" +
      " <areafile> <netcdf> <args>");
    System.out.println(" where <areafile> can be either a McIDAS area " +
      "file name, or an ADDE protocol URL");
    System.out.println("       <netcdf> will default to 'areafile.nc'");
    System.out.println("       <args> can be one of the following:");
    System.out.println("              -AWIPS: generate AWIPS format file");
    System.out.println("              -NOLL : no lat/lon in output file");
    System.out.println("              -Q : quiet - no progress bar output");
    System.out.println("              -GIF<filenme> : put the contents of the GIF/JPEG image");
    System.out.println("                  into NetCDF file; use with -AWIPS option ONLY!");
    System.out.println("              -C<calibration_type> : " +
      "output data in this calibration type");
    System.out.println("                where <calibration_type> is one of:");
    System.out.println("                  RAD:  radiance values");
    System.out.println("                  TEMP: temperature values");
    System.out.println("                  BRIT: brightness values");
    System.out.println("                  ALB:  albedo");
    System.out.println("                  RAW:  raw sensor values");
    System.out.println("              -CF : use CF-compliant attributes");
    return;
  }

  fInName = args[0];
  if (args.length > 1) {
    if ((args[1].indexOf("-NOLL") < 0) && 
        (args[1].indexOf("-C") < 0) &&
        (args[1].indexOf("-Q") < 0) &&
        (args[1].indexOf("-AWIPS") < 0)) {
      fOutName = args[1];
      if (fOutName.indexOf(".nc") < 0) {
        fOutName = fOutName + ".nc";
      }
    } else {
      fOutName = fInName + ".nc";
    }
  } else {
    fOutName = fInName + ".nc";
  }

  // look for "no lat/lon" flag, -NOLL and calibration flag, -C<caltype>, -AWIPS
  for (int i = 1; i < args.length; i++) {

    if (args[i].indexOf("-NOLL") >= 0) {
      navFlag = 0;
    }

    if (args[i].indexOf("-CF") >= 0) {
      useCF = true;
    }

    if (args[i].indexOf("-CTEMP") >= 0) {
      calTypeOut = Calibrator.CAL_TEMP;
    }

    if (args[i].indexOf("-CBRIT") >= 0) {
      calTypeOut = Calibrator.CAL_BRIT;
    }

    if (args[i].indexOf("-CALB") >= 0) {
      calTypeOut = Calibrator.CAL_ALB;
    }


    if (args[i].indexOf("-Q") >= 0) {
      isQuiet = true;
    }

    if (args[i].indexOf("-CRAW") >= 0) {
      calTypeOut = Calibrator.CAL_RAW;
    }

    if (args[i].indexOf("-AWIPS") >= 0) {
      System.out.println("AWIPS option selected...");
      awipsFlag = 1;
      navFlag = 0;
      calTypeOut = Calibrator.CAL_BRIT;
    }

    if (args[i].indexOf("-GIF") >= 0) {
      awipsFlag = 1;
      gifFlag = 1;
      navFlag = 0;
      GIFFilename = args[i].substring(4);
      System.out.println("GIF option selected...file="+GIFFilename);
      calTypeOut = Calibrator.CAL_BRIT;
    }

  }

  // some status info.  print filename only if not an image URL
  if (fInName.indexOf("image") < 0) {
    System.out.println("input filename: " + fInName);
  }
  System.out.println("output filename: " + fOutName);

  // create the science image object
  try {
    si = new ScienceImage(fInName);
    ad = si.getAD();
  } catch (IOException e) {
    System.out.println("Error creating ScienceImage " + e);
    return;
  }

  // only certain data sources are currently supported for navigation
  // current: GVAR only - implies that the line/element arrays 
  // must be ordered [element][line] for conversion
  int sid = ad.getSensorId();
  if ( ((sid < 70) || (sid > 86)) && ((sid < 180) || (sid > 187)) ){
    System.out.println("####  sid = "+sid+" ... no navigation available");
    navFlag = 0;
  }

  // set up a status message, used later
  if (navFlag != 0) {
    statusMsg = "Fetching, calibrating, navigating, storing data ";
  } else {
    statusMsg = "Fetching, calibrating, storing data ";
  }

  // get objects we'll work with below
  SensorData sd = si.getSD();
  Calibrator c = si.getC();
  Navigator  n = si.getN();

  // we'll use these throughout the conversion
  int sl = ad.getStartLine();
  int se = ad.getStartElem();

  try {

    Schema schema = new Schema();

    // set up dimensions
    Dimension y = new Dimension("y", ad.getNumLines());
    Dimension x = new Dimension("x", ad.getNumElements());
    Dimension lines = new Dimension("lines", ad.getNumLines());
    Dimension elements = new Dimension("elements", ad.getNumElements());
    Dimension numbands = new Dimension("numbands", ad.getNumBands());
    Dimension sidLength = new Dimension("stringlength", 128);

    // global attributes
    if (n instanceof NavigatorGvar) {
      schema.putAttribute(new Attribute("projName", "GVAR"));
    }

    if (n instanceof NavigatorGeos) {
      schema.putAttribute(new Attribute("projName", "GEOS"));
    }

    if ((n instanceof NavigatorLambertConformal) || 
        (n instanceof NavigatorMercator) ||
        (n instanceof NavigatorPolarStereographic)) {

      // lots of global attributes needed here
      if (n instanceof NavigatorLambertConformal) {
        // central lat/lon obtained from nav object methods
        cLat = ((NavigatorLambertConformal) n).getCentralLat();
        cLon = ((NavigatorLambertConformal) n).getCentralLon();
        schema.putAttribute(new Attribute("projName", "LAMBERT_CONFORMAL"));
        schema.putAttribute(new Attribute("projIndex", (int) 3));
        schema.putAttribute(new Attribute("rotation", cLat));
      } 

      if (n instanceof NavigatorMercator) {
        // central lat/lon obtained from nav object methods
        cLat = ((NavigatorMercator) n).getCentralLat();
        cLon = ((NavigatorMercator) n).getCentralLon();
        schema.putAttribute(new Attribute("projName", "MERCATOR"));
        schema.putAttribute(new Attribute("projIndex", (int) 2));
        schema.putAttribute(new Attribute("rotation", 0.0f));
      } 

      if (n instanceof NavigatorPolarStereographic) {
        // central lat/lon obtained from nav object methods
        cLat = ((NavigatorPolarStereographic) n).getCentralLat();
        cLon = ((NavigatorPolarStereographic) n).getCentralLon();
        schema.putAttribute(new Attribute("projName", "STEREOGRAPHIC"));
        schema.putAttribute(new Attribute("projIndex", (int) 1));
        schema.putAttribute(new Attribute("rotation", 0.0f));
      } 

      schema.putAttribute (
        new Attribute("centralLat", cLat)
      );
      schema.putAttribute (
        new Attribute("centralLon", cLon)
      );

      double tmpLatLon [][] = new double[2][1];
      double tmpLinEle [][] = new double[2][1];
      
      // compute lat/lon at upper left corner

      tmpLinEle[0][0] = sl;
      tmpLinEle[1][0] = se;
      tmpLatLon = n.toLatLon(tmpLinEle);
      schema.putAttribute (
        new Attribute("lat00", (float) tmpLatLon[0][0])
      );
      schema.putAttribute (
        new Attribute("lon00", (float) tmpLatLon[1][0])
      );

      // compute lat/lon at lower right corner
      tmpLinEle[0][0] = sl + (ad.getNumLines() - 1) * ad.getLineRes();
      tmpLinEle[1][0] = se + (ad.getNumElements() - 1) * ad.getElemRes();;
      tmpLatLon = n.toLatLon(tmpLinEle);
      schema.putAttribute (
        new Attribute("latNxNy", (float) tmpLatLon[0][0])
      );
      schema.putAttribute (
        new Attribute("lonNxNy", (float) tmpLatLon[1][0])
      );

      // compute lat/lon at center point
      tmpLinEle[0][0] = sl + ((ad.getNumLines() / 2) - 1) * ad.getLineRes();
      tmpLinEle[1][0] = se + ((ad.getNumElements() / 2) - 1) * ad.getElemRes();;
      tmpLatLon = n.toLatLon(tmpLinEle);
      schema.putAttribute (
        new Attribute("latDxDy", (float) tmpLatLon[0][0])
      );
      schema.putAttribute (
        new Attribute("lonDxDy", (float) tmpLatLon[1][0])
      );
      // store these values for distance calculation later
      lat1 = (float) tmpLatLon[0][0];
      lon1 = (float) tmpLatLon[1][0];

      // compute lat/lon one line down from center point
      tmpLinEle[0][0] = sl + 1 + ((ad.getNumLines() / 2) - 1) * ad.getLineRes();
      tmpLinEle[1][0] = se + ((ad.getNumElements() / 2) - 1) * ad.getElemRes();;
      tmpLatLon = n.toLatLon(tmpLinEle);
      // store these values for distance 
      lat2 = (float) tmpLatLon[0][0];
      lon2 = (float) tmpLatLon[1][0];

      // compute y resolution at center
      yRes = ConversionUtility.LatLonToDistance(lat1, lon1, lat2, lon2);

      // compute lat/lon one element over from center point
      tmpLinEle[0][0] = sl + ((ad.getNumLines() / 2) - 1) * ad.getLineRes();
      tmpLinEle[1][0] = se + 1 + 
        ((ad.getNumElements() / 2) - 1) * ad.getElemRes();;
      tmpLatLon = n.toLatLon(tmpLinEle);
      // store these values for distance 
      lat2 = (float) tmpLatLon[0][0];
      lon2 = (float) tmpLatLon[1][0];

      // compute x resolution at center
      xRes = ConversionUtility.LatLonToDistance(lat1, lon1, lat2, lon2);

      // store the resolution attributes
      schema.putAttribute (
        new Attribute("dxKm", xRes)
      );
      schema.putAttribute (
        new Attribute("dyKm", yRes)
      );

    }

    Date curDate = new Date();
    String encStr = "NetCDF encoded on " + curDate.toGMTString();
    schema.putAttribute(new Attribute("history", encStr));
    schema.putAttribute(new Attribute("version", VERSION));

    // read and convert area directory values 

    ProtoVariable adVersion = new ProtoVariable (
      "version",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adVersion);
    adVersion.putAttribute (
      new Attribute("long_name", "McIDAS area file version number")
    );

    ProtoVariable adSensorID = new ProtoVariable (
      "sensor_ID",
      Character.TYPE,
      sidLength
    );
    schema.put(adSensorID);
    adSensorID.putAttribute (
      new Attribute("long_name", "sensor identification")
    );

    ProtoVariable adImgDate = new ProtoVariable (
      "image_date",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adImgDate);
    adImgDate.putAttribute(new Attribute("units", "ccyyddd"));
    adImgDate.putAttribute (
      new Attribute("long_name", "image year and day of year")
    );

    ProtoVariable adImgTime = new ProtoVariable (
      "image_time",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adImgTime);
    adImgTime.putAttribute(new Attribute("units", "hhmmss UTC"));
    adImgTime.putAttribute (
      new Attribute("long_name", "image time in UTC")
    );

    ProtoVariable adSLine = new ProtoVariable (
      "start_line",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adSLine);
    adSLine.putAttribute(useCF ? new Attribute("units","count") : new Attribute("units", "satellite coordinates"));
    adSLine.putAttribute (
      new Attribute("long_name", "image starting line")
    );

    ProtoVariable adSElem = new ProtoVariable (
      "start_elem",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adSElem);
    adSElem.putAttribute(useCF ? new Attribute("units","count") : new Attribute("units", "satellite coordinates"));
    adSElem.putAttribute (
      new Attribute("long_name", "image starting element")
    );

    ProtoVariable adNLines = new ProtoVariable (
      "num_lines",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adNLines);
    adNLines.putAttribute (
      new Attribute("long_name", "number of lines")
    );

    ProtoVariable adNElems = new ProtoVariable (
      "num_elems",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adNElems);
    adNElems.putAttribute (
      new Attribute("long_name", "number of elements")
    );

    ProtoVariable adDataWidth = new ProtoVariable (
      "data_width",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adDataWidth);
    adDataWidth.putAttribute(useCF ? new Attribute("units","count") : new Attribute("units", "bytes/data point"));
    adDataWidth.putAttribute (
      new Attribute("long_name", "number of bytes per source data point")
    );

    ProtoVariable adLineRes = new ProtoVariable (
      "line_resolution",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adLineRes);
    adLineRes.putAttribute(new Attribute("units", "km"));
    adLineRes.putAttribute (
      new Attribute("long_name", "resolution of each pixel in line direction")
    );

    ProtoVariable adElemRes = new ProtoVariable (
      "elem_resolution",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adElemRes);
    adElemRes.putAttribute(new Attribute("units", "km"));
    adElemRes.putAttribute (
      new Attribute("long_name", "resolution of each pixel in elem direction")
    );

    ProtoVariable adPfxSize = new ProtoVariable (
      "prefix_size",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adPfxSize);
    adPfxSize.putAttribute(useCF ? new Attribute("units","count") : new Attribute("units", "bytes"));
    adPfxSize.putAttribute (
      new Attribute("long_name", "line prefix size")
    );

    ProtoVariable adProjNum = new ProtoVariable (
      "project_number",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adProjNum);

    ProtoVariable adCrDate = new ProtoVariable (
      "creation_date",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adCrDate);
    adCrDate.putAttribute(new Attribute("units", "ccyyddd"));
    adCrDate.putAttribute (
      new Attribute("long_name", "image creation year and day of year")
    );

    ProtoVariable adCrTime = new ProtoVariable (
      "creation_time",
      Integer.TYPE,
      (Dimension []) null
    );
    schema.put(adCrTime);
    adCrTime.putAttribute(new Attribute("units", "hhmmss UTC"));
    adCrTime.putAttribute (
      new Attribute("long_name", "image creation time in UTC")
    );

    // band array
    Dimension[] bandDim = new Dimension[1];
    bandDim[0] = numbands;

    ProtoVariable bands = new ProtoVariable("bands", Integer.TYPE, bandDim);
    if (awipsFlag == 0) {
      schema.put(bands);
      bands.putAttribute (
        new Attribute("long_name", "spectral bands present")
      );
    }

    // the image data
    Dimension[] dataDim = new Dimension[3];
    dataDim[0] = numbands;
    dataDim[1] = lines;
    dataDim[2] = elements;

    // use this one for -AWIPS option
    Dimension[] awipsDim = new Dimension[2];
    awipsDim[0] = y;
    awipsDim[1] = x;
    
    // lat, lon dimensions
    Dimension[] llDim = new Dimension[2];
    llDim[0] = lines;
    llDim[1] = elements;

    // data type depends on output calibration type
    ProtoVariable image;
    if (calTypeOut == Calibrator.CAL_BRIT) {
      if (awipsFlag == 1) {
        image = new ProtoVariable("image", Byte.TYPE, awipsDim);
      } else {
        image = new ProtoVariable("image", Byte.TYPE, dataDim);
      }
    } else {
      image = new ProtoVariable("image", Float.TYPE, dataDim);
    }
    schema.put(image);
    // data attributes depend on cal type
    switch (calTypeOut) {
      case Calibrator.CAL_BRIT:
        image.putAttribute (
          new Attribute("long_name", "brightness values")
        );
        image.putAttribute (
          useCF ? new Attribute("units","count") :
          new Attribute("units", "brightness counts")
        );
        break;
      case Calibrator.CAL_TEMP:
        image.putAttribute (
          new Attribute("long_name", "temperature values")
        );
        image.putAttribute (
          useCF ? new Attribute("units", "degK") : 
          new Attribute("units", "degrees Kelvin")
        );
        break;
      case Calibrator.CAL_ALB:
        image.putAttribute (
          new Attribute("long_name", "albedo")
        );
        image.putAttribute (
          new Attribute("units", "%")
        );
        break;
      case Calibrator.CAL_RAD:
      default:
        image.putAttribute (
          new Attribute("long_name", "pixel radiance values")
        );
        image.putAttribute (
          new Attribute("units", "mw/cm2/steradian/cm-1")
        );
        break;
    }


    ProtoVariable lats = new ProtoVariable("lats", Float.TYPE, llDim);
    ProtoVariable lons = new ProtoVariable("lons", Float.TYPE, llDim);

    /* do lat/lon nav only if nav flag is set */
    if (navFlag != 0) {

      schema.put(lats);
      lats.putAttribute (
        useCF ? new Attribute("units","degrees_north") : 
        new Attribute("units", "degrees North")
      );
      lats.putAttribute (
        new Attribute("long_name", "pixel latitude")
      );

      schema.put(lons);
      lons.putAttribute (
        useCF ? new Attribute("units","degrees_east") : 
        new Attribute("units", "degrees East")
      );
      lons.putAttribute (
        new Attribute("long_name", "pixel longitude")
      );

    }

    // finally, create the NetCDF object
    NetcdfFile nf = new NetcdfFile(fOutName, true, true, schema);

    // now, load all the actual data.  start with ancillary data
    int [] origin = {0};
    nf.get(adNLines.getName()).setInt(origin, ad.getNumLines());
    nf.get(adNElems.getName()).setInt(origin, ad.getNumElements());
    nf.get(adLineRes.getName()).setInt(origin, ad.getLineRes());
    nf.get(adElemRes.getName()).setInt(origin, ad.getElemRes());
    nf.get(adImgDate.getName()).setInt(origin, ad.getImageDate());
    nf.get(adImgTime.getName()).setInt(origin, ad.getImageTime());
    nf.get(adSLine.getName()).setInt(origin, ad.getStartLine());
    nf.get(adSElem.getName()).setInt(origin, ad.getStartElem());
    nf.get(adCrDate.getName()).setInt(origin, ad.getCreationDate());
    nf.get(adCrTime.getName()).setInt(origin, ad.getCreationTime());
    String sensorString;
    switch (sid) {

      case 54:
      case 55:
      case 56:
      case 57:
      case 58:
        sensorString = "MET-" + (sid - 51);
        break;

      case 60:
      case 61:
      case 62:
      case 63:
      case 64:
      case 65:
      case 66:
      case 67:
      case 68:
      case 69:
        sensorString = "NOAA-" + (sid - 50);
        break;

      case 70:
        sensorString = "GOES-8 Imager";
        break;

      case 71:
        sensorString = "GOES-8 Sounder";
        break;

      case 72:
        sensorString = "GOES-9 Imager";
        break;

      case 73:
        sensorString = "GOES-9 Sounder";
        break;

      case 74:
        sensorString = "GOES-10 Imager";
        break;

      case 75:
        sensorString = "GOES-10 Sounder";
        break;

      case 76:
        sensorString = "GOES-11 Imager";
        break;

      case 77:
        sensorString = "GOES-11 Sounder";
        break;

      case 78:
        sensorString = "GOES-12 Imager";
        break;

      case 79:
        sensorString = "GOES-12 Sounder";
        break;

      case 82:
      case 83:
      case 84:
      case 85:
      case 86:
        sensorString = "GMS-" + (sid - 78);
        break;

      case 89:
      case 90:
      case 91:
      case 92:
      case 93:
      case 94:
        sensorString = "DMSP-" + (sid - 79);
        break;

      case 180:
        sensorString = "GOES-13 Imager";

      case 181:
        sensorString = "GOES-13 Sounder";
      case 182:
        sensorString = "GOES-14 Imager";
      case 183:
        sensorString = "GOES-14 Sounder";

      case 184:
        sensorString = "GOES-15 Imager";
      case 185:
        sensorString = "GOES-15 Sounder";

      case 186:
        sensorString = "GOES-16 Imager";
      case 187:
        sensorString = "GOES-16 Sounder";

      default:
        sensorString = "unknown";
        break;

    }
    for (int i = 0; i < sensorString.length(); i++) {
      origin[0] = i;
      nf.get(adSensorID.getName()).setChar(origin, sensorString.charAt(i));
    }
    origin[0] = 0;

    nf.get(adVersion.getName()).setInt(origin, ad.getVersion());
    nf.get(adDataWidth.getName()).setInt(origin, ad.getDataWidth());
    nf.get(adPfxSize.getName()).setInt(origin, ad.getPrefixSize());
    nf.get(adProjNum.getName()).setInt(origin, ad.getProjectNum());

    // figure out exactly which bands are present
    int bandMap = ad.getBandMap();
    int bandIdx = 0;
    int bandArr[] = new int[ad.getNumBands()];
    for (int i = 0; i < 32; i++) {
      if (((bandMap >> i) & 0x0001) > 0) {
        bandArr[bandIdx] = i + 1;
        System.out.println("data present from channel: " + (i+1));
        bandIdx++;
      }
    }

    // stuff this list in the NetCDF file
    if (awipsFlag == 0) {
      ArrayMultiArray bandMA = new ArrayMultiArray((Object) bandArr);
      nf.get(bands.getName()).copyin(origin, bandMA);
    }

    // now, load and calibrate the raw data

    // set cal type if we have a valid calibrator
    c.setCalType(ad.getCalType());

    // allocate buffers for image line chunks of the array data
    float dataLine [][][] = null; 
    byte awipsLine [][] = null; 
    byte byteLine [][][] = null;

    if ((awipsFlag == 1) || (calTypeOut == Calibrator.CAL_BRIT)) {
      if (awipsFlag == 1) {
        awipsLine = new byte[ad.getNumBands()][ad.getNumElements()];
      } else {
        byteLine = new byte[ad.getNumBands()][1][ad.getNumElements()];
      }
    } else {
      dataLine = new float[ad.getNumBands()][1][ad.getNumElements()];
    }

    float tmpLine [] = 
      new float[ad.getNumElements() * ad.getNumBands()];

    // again, only do lat/lon if nav flag set 
    double linEles [][] = new double[2][ad.getNumElements()];;
    double latLons [][] = new double[2][ad.getNumElements()];;
    float latArr [][] = new float[1][ad.getNumElements()];
    float lonArr [][] = new float[1][ad.getNumElements()];
    if (navFlag != 0) {
      // initialize element part of this nav buffer - values won't change
      for (int element = 0; element < ad.getNumElements(); element++) {
        //linEles[1][element] = element + se; 

        // use file coordinates for GVAR
        linEles[0][element] = element; 
      }
    }

    // initialize the data, lat, lon indices
    // get handles to the variables we'll use

    // use this origin for -AWIPS option
    int [] awipsOrigin = new int[2];
    awipsOrigin[0] = 0;
    awipsOrigin[1] = 0;

    int [] dataOrigin = new int[3];
    dataOrigin[0] = 0;
    dataOrigin[1] = 0;
    dataOrigin[2] = 0;
    Variable dataV = nf.get(image.getName());

    int [] llOrigin = new int[2];
    llOrigin[0] = 0;
    llOrigin[1] = 0;
    Variable latsV = nf.get(lats.getName());
    Variable lonsV = nf.get(lons.getName());

    // fetch, calibrate, and store all the data lines
    System.out.println("Image date/time = "+ad.getImageDate()+" / "+
        ad.getImageTime());
    System.out.print(statusMsg); 
    charCount = statusMsg.length();
    int[] pixels=null;
    int width=0;
    int height=0;
    if (gifFlag != 0) {
      try {
        MediaTracker mt = new MediaTracker(this);
        Image img = Toolkit.getDefaultToolkit().getImage(GIFFilename);
        mt.addImage(img,0);
        mt.waitForAll();
        width = img.getWidth(this);
        height = img.getHeight(this);
        if (!isQuiet) System.out.println("GIF/JPEG height/width = "+height+" "+width);
        pixels = new int[ width * height ];
        PixelGrabber pg = new
               PixelGrabber(img,0,0,width,height,pixels,0,width);
        pg.grabPixels();
        int stat = pg.status();
        //if ((stat & ImageObserver.ABORT) != 0) System.out.println("ABORT");
        if (!isQuiet & (stat & ImageObserver.ALLBITS) != 0) System.out.println("GIF ALLBITS");
        //if ((stat & ImageObserver.ERROR) != 0) System.out.println("ERROR");
        //if ((stat & ImageObserver.FRAMEBITS) != 0) System.out.println("FRAMEBITS");
        //if ((stat & ImageObserver.HEIGHT) != 0) System.out.println("HEIGHT");
        //if ((stat & ImageObserver.SOMEBITS) != 0) System.out.println("SOMEBITS");
        //if ((stat & ImageObserver.WIDTH) != 0) System.out.println("WIDTH");
        //if ((stat & ImageObserver.PROPERTIES) != 0) System.out.println("PROPERTIES");
        
      } catch (Exception mte) {System.out.println(mte);}

    }

    int[] rgb = new int[256];
    int maxrgb = 0;
    rgb[0] = 0;

     if (!isQuiet) System.out.println("AREA lines/elements="+
         ad.getNumLines()+" / "+ ad.getNumElements());

    for (int lineNum = 0; lineNum < ad.getNumLines(); lineNum++) {
      if (!isQuiet) {
        System.out.print(".");
        charCount++;
        if (charCount == 72) {
          charCount = 0;
          System.out.println("");
        }
      }
      if (gifFlag == 0) tmpLine = sd.nextLine(tmpLine);

      // calibrate the data
      int numBands = ad.getNumBands();
      for (bandIdx = 0; bandIdx < ad.getNumBands(); bandIdx++) {

        for (int element = 0; element < ad.getNumElements(); element++) {
          if (calTypeOut == Calibrator.CAL_BRIT) {
            if (awipsFlag == 1) {
              if (gifFlag == 1 ) {
                int val= pixels[element+lineNum*width]; // get pixel from GIF
                val = val & 0x00ffffff;
                int gotit=-1;
                for (int ki =0; ki<=maxrgb; ki++) {
                  if (val == rgb[ki]) { gotit=ki;  break;}
                }
                if (gotit < 0) {
                  maxrgb++;
                  if (maxrgb > 255) maxrgb = 255;
                  rgb[maxrgb] = val;
                  gotit = maxrgb;
                }

                awipsLine[0][element] = (byte) gotit;

              } else {
                awipsLine[bandIdx][element] = (byte) 
                  c.calibrate (
                    tmpLine[bandIdx + (element * numBands)],
                    bandArr[bandIdx], 
                    calTypeOut
                  );
              }
            } else {
              byteLine[bandIdx][0][element] = (byte) 
                c.calibrate (
                  tmpLine[bandIdx + (element * numBands)],
                  bandArr[bandIdx], 
                  calTypeOut
                );
            }
          } else {

            dataLine[bandIdx][0][element] = 
              c.calibrate (
                tmpLine[bandIdx + (element * numBands)],
                bandArr[bandIdx], 
                calTypeOut
              );
          }
        }
      }

      // store this line
      dataOrigin[1] = lineNum;
      awipsOrigin[0] = lineNum;
      if (calTypeOut == Calibrator.CAL_BRIT) {
        if (awipsFlag == 1) {
          dataV.copyin ( awipsOrigin, new ArrayMultiArray(awipsLine) );
        } else {
          dataV.copyin ( dataOrigin, new ArrayMultiArray(byteLine) );
        }
      } else {
        dataV.copyin ( dataOrigin, new ArrayMultiArray(dataLine) );
      }

      if (navFlag != 0) {

        // set up the nav buffer for this line
        for (int element = 0; element < ad.getNumElements(); element++) {
          //linEles[0][element] = lineNum + sl; 
          // use file coordinates for GVAR
          linEles[1][element] = lineNum; 
        }

        // do the transformation for this line
        if (n != null) {
          latLons = n.toLatLon(linEles);
        }

        // copy these into the big lat, lon buffers
        for (int element = 0; element < ad.getNumElements(); element++) {
          latArr[0][element] = (float) latLons[0][element];
          lonArr[0][element] = (float) latLons[1][element];
        }

        // store these lats and lons
        llOrigin[0] = lineNum;

        latsV.copyin ( llOrigin, new ArrayMultiArray(latArr) );
        lonsV.copyin ( llOrigin, new ArrayMultiArray(lonArr) );

      }

    }

    System.out.println("");
    for (int ki=0; ki<maxrgb; ki++) {
      if (!isQuiet) System.out.println("rgb["+ki+"] = "+Integer.toHexString(rgb[ki]));
    }

    // close the object out
    System.out.println(encStr + " using AreaToNetCDF version " + VERSION);
    nf.close();

  } catch (Exception e) {
    e.printStackTrace();
    System.out.println(e);
  }

  }

}
