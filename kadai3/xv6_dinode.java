import java.nio.ByteBuffer;
import java.util.ArrayList;

public class xv6_dinode {
  public static final int DINODE_SIZE = 64;

  // dinode data
  public final int    type;
  public final int    major;
  public final int    minor;
  public final int    nlink;
  public final long   size;
  public final long[] addrs;

  // dinode analyzed data
  public boolean unused;
  public boolean invalid;
  public int     directory_links = 0;

  // dinode directory data
  public ArrayList<xv6_dirent> dirents = new ArrayList<>();
  public int                   parent_inode         = 0;

  public xv6_dinode(ByteBuffer bb, xv6_img_file img) {
    type = xv6_util.unsign_short(bb.getShort());
    major = xv6_util.unsign_short(bb.getShort());
    minor = xv6_util.unsign_short(bb.getShort());
    nlink = xv6_util.unsign_short(bb.getShort());
    size = xv6_util.unsign_int(bb.getInt());
    addrs = new long[13];
    for (int i = 0; i < 13; i++) {
      addrs[i] = xv6_util.unsign_int(bb.getInt());
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