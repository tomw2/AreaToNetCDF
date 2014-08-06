
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.String;
import edu.wisc.ssec.mcidas.AncillaryData;

/**
 * SensorData creates a object used to gather image data on a line
 * by line basis.  Only access method at present is nextLine.
 *
 * @version 1.3 6 Aug 1999
 * @author Tommy Jasmin, SSEC
 */

class SensorData {

  private DataInputStream dis;
  private AncillaryData ad;
  private int dw = 0;
  private int nb = 0;
  private int ps = 0;
  private boolean isSwapped;

  /**
   *
   * constructor
   *
   * @param DIS         data input stream
   * @param AD          AncillaryData object
   *
   */

  public SensorData(DataInputStream DIS, AncillaryData AD) 

  {
    // store references passed in
    dis = DIS;
    ad  = AD;

    // allocate the line buffer we'll use
    // dataLine = new float[ad.getNumElements()][ad.getNumBands()];
   
    // store data width, needed so nextLine knows how many bytes to read
    dw = ad.getDataWidth();

    // store number of bands, needed for value counts to read in 
    nb = ad.getNumBands();

    // store line prefix size
    ps = ad.getPrefixSize();
    isSwapped = ad.isSwapped();
  }

  /**
   *
   * load next line of data into float array provided
   *
   * @param dataLine	buffer to return data in
   *
   */

  public float[] nextLine(float [] dataLine) 
    throws IOException 
  {
    byte tmpByte;
    int tmpInt;

    // first, eat any line prefix bytes
    for (int i = 0; i < ps; i++) {
      tmpByte = dis.readByte();
    }

    switch (dw) {
      case 1:
        for (int i = 0; i < ad.getNumElements() * ad.getNumBands(); i++) {
          dataLine[i] = (float) dis.readByte();
        }
        break;
      case 4:
        for (int i = 0; i < ad.getNumElements() * ad.getNumBands(); i++) {
          tmpInt = dis.readInt();

          if (isSwapped) {
            dataLine[i] = (float) ((( (tmpInt >> 16) & 0xff) << 8 ) | 
                                       ( (tmpInt >> 24) & 0xff));
          } else {
            dataLine[i] = (float)tmpInt;
          }
        }
        break;
      case 2:
      default:
        for (int i = 0; i < ad.getNumElements() * ad.getNumBands(); i++) {
          tmpInt = ((int) dis.readShort() & 0xFFFF);
          if (isSwapped) {
            dataLine[i] = (float) (( (tmpInt & 0xff) << 8 ) | 
                                      ( (tmpInt >> 8) & 0xff)) ;

          } else {
            dataLine[i] = (float) tmpInt;
          }
        }
        break;
    }

    return dataLine;
  }

}
