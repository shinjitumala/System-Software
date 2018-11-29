import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class xv6_img_file {
  public static final int BLOCK_SIZE       = 512;
  public static final int DINODE_PER_BLOCK = 512 / xv6_dinode.DINODE_SIZE;

  private final String path;
  private final byte[] bytes;

  // super block data
  public final long size;
  public final long nblocks;
  public final long ninodes;
  public final long nlog;
  public final long logstart;
  public final long inodestart;
  public final long bmapstart;

  public final xv6_dinode[] dinodes;
  private int[]             block_reference_count;
  private boolean[]         bmap;

  // super block test
  public boolean super_block_error = false; // true if there is an error with the super block
                                            // data.
  public int     block_data_error  = 0;     // data error count.

  // bmap test
  public boolean bmap_error               = false; // true if bmap has an error.
  public int     bmap_error_used          = 0;     // Error: No inode referenced a block but was in
                                                   // use accourding to bmap.
  public int     bmap_error_unused        = 0;     // Error: inode referenced a block but was not
                                                   // used according to bmap.
  public int     bmap_multiple_references = 0;     // Error: Multiple inodes were referencing the
                                                   // same block.
  // inode test
  public boolean inode_error               = false; // true if any of the inodes have an error.
  public int     inode_type_error          = 0;     // inode file type errors.
  public int     inode_directory_error     = 0;     // inode directory reference errors.
  public int     inode_block_address_error = 0;     // inode block reference errors.
  public int     inode_size_error          = 0;     // inode size errors.

  public int     error_count = 0;
  public boolean size_error  = false;

  public xv6_img_file(String path) throws IOException, InterruptedException {
    // ファイル読み込み
    this.path = path;
    File file = new File(path);
    FileInputStream fis = new FileInputStream(file);
    bytes = new byte[Math.toIntExact(file.length())];
    fis.read(bytes);
    fis.close();

    // スーパーブロック読み込み
    ByteBuffer super_block = get_block(1);
    size = xv6_util.unsign_int(super_block.getInt());
    nblocks = xv6_util.unsign_int(super_block.getInt());
    ninodes = xv6_util.unsign_int(super_block.getInt());
    nlog = xv6_util.unsign_int(super_block.getInt());
    logstart = xv6_util.unsign_int(super_block.getInt());
    inodestart = xv6_util.unsign_int(super_block.getInt());
    bmapstart = xv6_util.unsign_int(super_block.getInt());

    // inodeブロック読み込み
    dinodes = get_inodes();

    // bmapブロックを読み込み
    bmap = new boolean[Math.toIntExact(size)];
    // only considers when bmap uses one block.
    if (true) {
      boolean[] bmap_block_data = get_block_as_bits(Math.toIntExact(bmapstart));
      bmap = Arrays.copyOfRange(bmap_block_data, 0, Math.toIntExact(size));
    }

    System.out.println("****************************************************************");
    System.out.println(path + ":");
    System.out.println("----------------------------------------------------------------");
    System.out.println(super_block_test());
    System.out.println("----------------------------------------------------------------");
    System.out.println(inode_test());
    System.out.println("----------------------------------------------------------------");
    System.out.println(bmap_test());
    System.out.println("****************************************************************");
    System.out.println();

    // else {
    // System.err.println("Not implemented.");
    // System.exit(1);
    // }

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
    ByteBuffer bb = ByteBuffer
        .wrap(Arrays.copyOfRange(bytes, i * BLOCK_SIZE, (i + 1) * BLOCK_SIZE));
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
    boolean data[] = new boolean[BLOCK_SIZE * 8];
    int pos = 0;
    while (bb.hasRemaining()) {
      byte b = bb.get();
      for (int k = 0; k < 8; k++) {
        data[pos++] = ((b >> k) & 1) == 1;
      }
    }
    return data;
  }

  /**
   * Gets the content of a block as a integer array
   *
   * @param i Block number
   * @return
   */
  public long[] get_block_as_long(int i) {
    ByteBuffer bb = get_block(i);
    long data[] = new long[BLOCK_SIZE / 4];
    int pos = 0;
    while (bb.hasRemaining()) {
      long l = xv6_util.unsign_int(bb.getInt());
      data[pos++] = l;
    }
    return data;
  }

  private xv6_dinode[] get_inodes() {
    xv6_dinode inodes[] = new xv6_dinode[Math.toIntExact(ninodes)];
    int i = 0;
    int block_number = Math.toIntExact(inodestart);
    while (i < ninodes) {
      ByteBuffer bb = get_block(block_number);
      while ((i < ninodes) && bb.hasRemaining()) {
        inodes[i] = new xv6_dinode(bb, this);
        i++;
      }
      block_number++;
    }
    return inodes;
  }

  /**
   * スーパーブロックに関する一貫性のテスト結果を文字列で返す。
   *
   * @return
   */
  public String super_block_test() {
    /**************************************************************************/
    // sizeがファイルシステムの総ブロック数になっているか。
    /**************************************************************************/
    if (!((bytes.length / BLOCK_SIZE) == size)) {
      super_block_error = true;
      System.err.println("Error <super block>: 'size' does not match actual file size.");
      block_data_error++;
    }
    /**************************************************************************/

    /**************************************************************************/
    // nblocks, ninodes, nlogがそれぞれデータブロック, inode, ログブロックの数になっているか。
    // logstart, inodestart,
    // bmapstartがそれぞれログブロック，inodeブロック，ビットマップブロックの先頭ブロックの番号になっているか。
    /**************************************************************************/
    int block_count = 2;
    if (block_count != logstart) {
      super_block_error = true;
      System.err.println("Error <super block>: 'logstart' is corrupted.");
      block_data_error++;
    }

    block_count += nlog;
    if (block_count != inodestart) {
      super_block_error = true;
      System.err.println("Error <super block>: 'inodestart' is corrupted.");
      block_data_error++;
    }

    int dinodes_blocks = (Math.toIntExact(ninodes / DINODE_PER_BLOCK) + 1);
    block_count += dinodes_blocks;
    if (block_count != bmapstart) {
      super_block_error = true;
      System.err.println("Error <super block>: 'bmapstart' is corrupted.");
      block_data_error++;
    }

    int bmap_blocks = Math.toIntExact(((size / (BLOCK_SIZE * 8)) + 1));
    block_count += bmap_blocks;
    block_count += Math.toIntExact(nblocks);
    if (size != block_count) {
      super_block_error = true;
      block_data_error++;
      System.err
          .println(
              "Error <super block>: 'size' contradicts the total block count calculated from other data.");
    }

    if (block_data_error > 0) {
      // 明らかに違うデータが、それぞれのブロックで入っていれば、エラーとする
      super_block_error = true;
      System.err
          .println(
              "Error <super block>: Invalid data found. It is very likely that the super block data is corrupted.");
    }
    /**************************************************************************/

    return "super block test: " + ((super_block_error) ? "failed" : "passed") + "\n\tFound "
        + block_data_error + " data errors.";
  }

  /**
   * ブロックの使用状況に関する一貫性のテスト結果を文字列として返す
   *
   * @return
   */
  public String bmap_test() {
    /**************************************************************************/
    // ビットマップブロックに格納されているビットマップが，各ブロックの使用/未使用を正しく表しているか。
    // 使用されている各データブロックは，ただ一つのinodeあるいは間接参照ブロックから参照されているか。
    /**************************************************************************/
    block_reference_count = new int[Math.toIntExact(size)];
    for (xv6_dinode inode : dinodes) {
      if (!inode.invalid && !inode.unused) {
        for (int k = 0; k < 13; k++) {
          long i = inode.addrs[k];
          if (((size - nblocks - 1) < i) && (i < size)) {
            block_reference_count[Math.toIntExact(i)]++;
            if (k == 12) {
              // indirect reference
              long[] data = get_block_as_long(Math.toIntExact(i));
              for (long n : data) {
                if ((n != 0) & ((((bmapstart - nblocks - 1) < n)) && (n < size))) {
                  block_reference_count[Math.toIntExact(n)]++;
                }
              }
            }
          } else if (i == 0) { // unused reference
            // do nothing
          }
        }
      }
    }
    if ((size - nblocks) < 0) {
      System.err
          .println(
              "Error <bmap       >: Cannot continue with bmap test due to critical error in super block data.");
      return "bmap test: failed\n\tTest failed due to critical data corruption in super block.";
    } else {
      for (int i = Math.toIntExact((size - nblocks)); i < size; i++) {
        if (block_reference_count[i] > 1) {
          bmap_multiple_references++;
          System.err.println("Error <bmap       >: Multiple reference error. (" + i + ")");
        } else if ((block_reference_count[i] == 1) && !bmap[i]) {
          bmap_error_unused++;
          System.err.println("Error <bmap       >: Incorrect 'unused' status error. (" + i + ")");
        } else if ((block_reference_count[i] == 0) && bmap[i]) {
          bmap_error_used++;
          System.err.println("Error <bmap       >: Incorrect 'used' status error. (" + i + ")");
        }
      }

      bmap_error = (bmap_error_unused > 0) || (bmap_error_used > 0)
          || (bmap_multiple_references > 0);
    }
    /**************************************************************************/
    return "bmap test: " + ((bmap_error) ? "failed" : "passed") + "\n\tFound "
        + bmap_multiple_references + " inode multiple reference error." + "\n\tFound "
        + bmap_error_unused + " incorrect 'unused' flag errors." + "\n\tFound " + bmap_error_used
        + " incorrect 'used' flag errors.";
  }

  /**
   * inodeに関する一貫性のテストの結果を文字列として返す。
   *
   * @return
   */
  public String inode_test() {
    for (int k = 0; k < ninodes; k++) {
      xv6_dinode inode = dinodes[k];
      /**************************************************************************/
      // typeが正しいファイルタイプになっているか。typeがT_DEVの場合、major, minorが記されているか。
      /**************************************************************************/
      if (inode.type == 0) {
        inode.unused = true;
      } else if ((inode.type != 1) && (inode.type != 2) && (inode.type != 3)) {
        inode.invalid = true;
        System.err.println("Error <inode      >: Unknown 'type'.");
        inode_type_error++;
      } else if (inode.type == 3) {
        if ((inode.major == 0) || (inode.minor == 0)) {
          System.err.println("Error <inode      >: Device number is invalid.");
          inode_type_error++;
          inode.invalid = true;
        }
      } else if ((inode.major != 0) || (inode.minor != 0)) {
        System.err.println("Error <inode      >: Device number is set for non-device inode.");
        inode_type_error++;
        inode.invalid = true;
      }
      /**************************************************************************/

      /**************************************************************************/
      // nlinkが各ディレクトリからの正しい総参照数になっているか。
      /**************************************************************************/
      if (inode.unused && (inode.nlink != 0)) {
        inode.invalid = true;
        System.err.println("Error <inode      >: Unused inode has non-zero 'nlink'");
        inode_type_error++;
      }
      /**************************************************************************/

      /**************************************************************************/
      // addrs(および間接参照ブロック)が使用済みデータブロックを参照しているか。
      /**************************************************************************/
      int referenced_blocks = 0;
      for (int l = 0; l < 13; l++) {
        long i = inode.addrs[l];
        if (inode.unused && (i != 0)) {
          inode.invalid = true;
          System.err.println("Error <inode      >: Unused inode has non-zero 'addrs'");
          inode_type_error++;
        } else if (i == 0) {
          // do nothing
        } else if (((size - nblocks - 1) < i) && (i < size)) {
          if (!bmap[(int) i]) {
            inode_block_address_error++;
            inode.invalid = true;
            System.err.println("Error <inode      >: addrs referenced unused block. (" + i + ")");
          } else {
            if (l == 12) {
              // indirect reference
              long[] data = get_block_as_long((int) i);
              for (long n : data) {
                if ((((size - nblocks - 1) < n)) && (n < size)) {
                  if (!bmap[Math.toIntExact(n)]) {
                    inode_block_address_error++;
                    System.err
                        .println("Error <inode      >: addrs referenced unused block. (" + n + ")");
                    inode.invalid = true;
                  } else {
                    referenced_blocks++;
                  }
                } else if (n == 0) {
                  // do nothing
                } else {
                  inode.invalid = true;
                  System.err
                      .println("Error <inode      >: addrs out of bounds indirect reference. (" + i
                          + ")");
                  inode_block_address_error++;
                  inode.invalid = true;
                }
              }
            } else {
              // direct reference
              referenced_blocks++;
            }
          }
        } else {
          inode.invalid = true;
          System.err
              .println("Error <inode      >: addrs out of bounds direct reference. (" + i + ")");
          inode.invalid = true;
          inode_block_address_error++;
        }
      }
      /**************************************************************************/

      /**************************************************************************/
      // addrs(および間接参照ブロック)が参照しているデータブロック数がsizeと矛盾していないか。
      /**************************************************************************/
      if (inode.unused && (inode.size != 0)) {
        inode.invalid = true;
        System.err.println("Error <inode      >: Unused inode has non-zero 'size'");
        inode_type_error++;
        inode.invalid = true;
      } else if (!inode.unused) {
        if (Math.ceil((double) inode.size / BLOCK_SIZE) != referenced_blocks) {
          System.err.println("Error <inode      >: 'size' is invalid.");
          inode_size_error++;
          inode.invalid = true;
        }
      }
      /**************************************************************************/

      if (inode.invalid) {
        System.err.println(inode);
      }
    }
    inode_error = (inode_type_error > 0) || (inode_directory_error > 0)
        || (inode_block_address_error > 0) || (inode_size_error > 0);

    return "inode test: " + ((inode_error) ? "failed" : "passed") + "\n\tFound " + inode_type_error
        + " type errors." + "\n\tFound " + inode_directory_error + " directory errors."
        + "\n\tFound " + inode_block_address_error + " address error." + "\n\tFound "
        + inode_size_error + " size errors.";
  }

  // @Override
  // public String toString() {
  // return path + ":\n"
  // // + "size: " + size + "\n" + "nblocks: " + nblocks + "\nninodes: " + ninodes
  // // + "\nnlog: " + nlog + "\nlogstart: " + logstart + "\ninodestart: " +
  // // inodestart
  // // + "\nbmapstart: " + bmapstart + "\nFound " + error_count + "
  // errors.\n\tbmap
  // // errors: "
  // // + bmap_error + "\n\tdinode errors: " + "\n\tsize error: " + size_error +
  // "\n"
  // ;
  // }
}