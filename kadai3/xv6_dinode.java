import java.nio.ByteBuffer;

public class xv6_dinode {
  public final int    type;
  public final int    major;
  public final int    minor;
  public final int    nlink;
  public final long   size;
  public final long[] addrs;

  public xv6_dinode(ByteBuffer bb) {
    boolean unused = false;
    type = xv6_util.unsign_short(bb.getShort());
    if ((type != 1) && (type != 2) && (type != 3)) {
      if (type == 0) {
        unused = true;
      } else {
        System.err.println("xv6_dinode: 'type' error.");
        xv6_img_tester.error();
      }
    }

    major = xv6_util.unsign_short(bb.getShort());
    if ((major != 0) && unused) {
      System.err.println("xv6_dinode: 'major' error.");
    }

    minor = xv6_util.unsign_short(bb.getShort());
    if ((minor != 0) && unused) {
      System.err.println("xv6_dinode: 'minor' error.");
    }

    nlink = xv6_util.unsign_short(bb.getShort());
    if ((nlink != 0) && unused) {
      System.err.println("xv6_dinode: 'nlink' error.");
    }

    size = xv6_util.unsign_int(bb.getInt());
    if ((size != 0) && unused) {
      System.err.println("xv6_dinode: 'size' error.");
    }

    addrs = new long[13];
    for (int i = 0; i < 13; i++) {
      addrs[i] = xv6_util.unsign_int(bb.getInt());
      if ((addrs[i] != 0) && unused) {
        System.err.println("xv6_dinode: 'addrs' error.");
      }
    }
  }

  @Override
  public String toString() {
    String s_type;
    switch (type) {
      case 0:
        s_type = "UNUSED";
      break;
      case 1:
        s_type = "T_DIR";
      break;
      case 2:
        s_type = "T_FILE";
      break;
      case 3:
        s_type = "T_DEV";
      break;
      default:
        s_type = "ERROR_UNKNOWN(" + type + ")";
      break;
    }

    StringBuilder addrs_array = new StringBuilder();
    addrs_array.append("[");
    for (long l : addrs) {
      addrs_array.append(l + ", ");
    }
    addrs_array.append("]");
    return "type: " + s_type + "\nmajor: " + major + "\nminor: " + minor + "\nnlink: " + nlink
        + "\nsize: " + size + "\naddrs: " + addrs_array;
  }
}