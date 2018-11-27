import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class xv6_img_file {
  private final byte[] bytes;

  public final long size;
  public final long nblocks;
  public final long ninodes;
  public final long nlog;
  public final long logstart;
  public final long inodestart;
  public final long bmapstart;

  public final List<xv6_dinode> dinodes;

  public xv6_img_file(String path) throws IOException, InterruptedException {
    File file = new File(path);
    FileInputStream fis = new FileInputStream(file);
    bytes = new byte[(int) file.length()];
    fis.read(bytes);
    fis.close();

    ByteBuffer super_block = get_block(1);
    size = xv6_util.unsign_int(super_block.getInt());
    nblocks = xv6_util.unsign_int(super_block.getInt());
    ninodes = xv6_util.unsign_int(super_block.getInt());
    nlog = xv6_util.unsign_int(super_block.getInt());
    logstart = xv6_util.unsign_int(super_block.getInt());
    inodestart = xv6_util.unsign_int(super_block.getInt());
    bmapstart = xv6_util.unsign_int(super_block.getInt());

    dinodes = get_inodes();
  }

  /**
   * Gets the ByteBuffer for the entire file.
   *
   * @return
   */
  public ByteBuffer get_bytebuffer() {
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    return bb;
  }

  /**
   * Gets the ByteBuffer of a specific block.
   *
   * @param i Block number
   * @return
   */
  public ByteBuffer get_block(int i) {
    ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i * 512, (i + 1) * 512));
    bb.order(ByteOrder.LITTLE_ENDIAN);
    return bb;
  }

  /**
   * Gets the content of a block as a boolean array
   *
   * @param i Block number
   * @return
   */
  public boolean[] get_block_as_bits(int i) {
    ByteBuffer bb = get_block(i);
    boolean data[] = new boolean[512 * 8];
    int pos = 0;
    while (bb.hasRemaining()) {
      byte b = bb.get();
      for (int k = 0; k < 8; k++) {
        data[pos++] = ((b >> k) & 1) == 1;
      }
    }
    return data;
  }

  public ArrayList<xv6_dinode> get_inodes() {
    ArrayList<xv6_dinode> inodes = new ArrayList<>();
    int i = 0;
    int block_number = (int) inodestart;
    while (i < ninodes) {
      ByteBuffer bb = get_block(block_number++);
      while ((i < ninodes) && bb.hasRemaining()) {
        inodes.add(new xv6_dinode(bb));
        i++;
      }
    }
    return inodes;
  }

  @Override
  public String toString() {
    return "size: " + size + "\n" + "nblocks: " + nblocks + "\nninodes: " + ninodes + "\nnlog: "
        + nlog + "\nlogstart: " + logstart + "\ninodestart: " + inodestart + "\nbmapstart: "
        + bmapstart;
  }
}