import java.nio.ByteBuffer;

public class xv6_dirent {
  private static final int DIRECTORY_SIZE = 14;

  public final int    inum;
  public final byte[] name; // char in C is 1 byte.

  public xv6_dirent(ByteBuffer bb) {
    inum = xv6_util.unsign_short(bb.getShort());
    name = new byte[DIRECTORY_SIZE];
    for (int i = 0; i < DIRECTORY_SIZE; i++) {
      name[i] = bb.get();
    }

    // throws an exception if dirent is empty.
    if (inum == 0) {
      throw new IllegalArgumentException();
    }
  }
}