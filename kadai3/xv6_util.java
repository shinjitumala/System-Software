public class xv6_util {
  /**
   * Changes signed integer to unsigned integer
   *
   * @param i Signed integer
   * @return Unsigned integer but the type is long
   */
  public static long unsign_int(int i) {
    return i & 0x00000000ffffffffL;
  }

  /**
   * Changes signed short to unsigned short
   *
   * @param i Signed short
   * @return Unsigned short but the type is integer
   */
  public static int unsign_short(short i) {
    return i & 0x0000ffff;
  }
}