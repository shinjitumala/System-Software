import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class xv6_img_file {
  private static final int BLOCK_SIZE       = 512;
  private static final int DINODE_PER_BLOCK = 512 / xv6_dinode.DINODE_SIZE;
  private static final int NUMBER_DIRECT    = 12;

  // raw data
  private final String path;
  private final byte[] bytes;

  // super block data
  private final long size;
  private final long nblocks;
  private final long ninodes;
  private final long nlog;
  private final long logstart;
  private final long inodestart;
  private final long bmapstart;

  private final xv6_dinode[] dinodes;
  private int[]              block_reference_count;
  private boolean[]          bmap;

  // super block test
  private boolean super_block_error        = false; // true if there is an error with the super
                                                    // block
                                                    // data.
  private boolean super_block_size_error   = false; // true if size is not correct.
  private boolean super_block_inconsistent = false; // true if super block data contains some
                                                    // inconsistencies.

  // bmap test
  private boolean bmap_error               = false; // true if bmap has an error.
  private int     bmap_error_used          = 0;     // Error: No inode referenced a block but was in
                                                    // use accourding to bmap.
  private int     bmap_error_unused        = 0;     // Error: inode referenced a block but was not
                                                    // used according to bmap.
  private int     bmap_multiple_references = 0;     // Error: Multiple inodes were referencing the
                                                    // same block.
  // inode test
  private boolean inode_error               = false; // true if any of the inodes have an error.
  private int     inode_type_error          = 0;     // inode file type errors.
  private int     inode_directory_error     = 0;     // inode directory reference errors.
  private int     inode_block_address_error = 0;     // inode block reference errors.
  private int     inode_size_error          = 0;     // inode size errors.

  // directory test
  private boolean directory_error              = false; // true if there is any error in the
                                                        // directory.
  private int     directory_unused_inode_error = 0;     // unused inode referencing errors.
  private int     directory_self_parent_error  = 0;     // errors with '.' and '..'.

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
    System.out.println(this.path + ":");
    System.out.println("----------------------------------------------------------------");
    System.out.println(super_block_test());
    System.out.println("----------------------------------------------------------------");
    System.out.println(inode_test());
    System.out.println("----------------------------------------------------------------");
    System.out.println(bmap_test());
    System.out.println("----------------------------------------------------------------");
    System.out.println(directory_test());
    System.out.println("****************************************************************");
    System.out.println();
  }

  /**
   * Gets the ByteBuffer for the entire file.
   *
   * @return
   */
  private ByteBuffer get_bytebuffer() {
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
  private ByteBuffer get_block(int i) {
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
  private boolean[] get_block_as_bits(int i) {
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

  /**
   * Reads all the inodes from the inode blocks.
   *
   * @return
   */
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
   * Reads a block as a directory.
   *
   * @param i Block number
   * @return
   */
  private List<xv6_dirent> get_dirents(int i) {
    ArrayList<xv6_dirent> dirents = new ArrayList<>();
    ByteBuffer bb = get_block(i);
    while (bb.hasRemaining()) {
      xv6_dirent d;
      try {
        d = new xv6_dirent(bb);
      } catch (IllegalArgumentException e) {
        break;
      }
      dirents.add(d);
    }
    return dirents;
  }

  /**
   * スーパーブロックに関する一貫性のテスト結果を文字列で返す。
   *
   * @return
   */
  private String super_block_test() {
    /**************************************************************************/
    // sizeがファイルシステムの総ブロック数になっているか。
    /**************************************************************************/
    if (!((bytes.length / BLOCK_SIZE) == size)) {
      super_block_error = true;
      System.err.println("Error <super block>: 'size' does not match actual file size.");
      super_block_size_error = true;
    }
    /**************************************************************************/

    /**************************************************************************/
    // nblocks, ninodes, nlogがそれぞれデータブロック, inode, ログブロックの数になっているか。
    // logstart, inodestart,
    // bmapstartがそれぞれログブロック，inodeブロック，ビットマップブロックの先頭ブロックの番号になっているか。
    /**************************************************************************/
    int block_count = 2;
    if (block_count != logstart) {
      super_block_inconsistent = true;
      System.err.println("Error <super block>: 'logstart' is corrupted.");
    }

    block_count += nlog;
    if (block_count != inodestart) {
      super_block_inconsistent = true;
      System.err.println("Error <super block>: 'inodestart' is corrupted.");
    }

    int dinodes_blocks = (Math.toIntExact(ninodes / DINODE_PER_BLOCK) + 1);
    block_count += dinodes_blocks;
    if (block_count != bmapstart) {
      super_block_inconsistent = true;
      System.err.println("Error <super block>: 'bmapstart' is corrupted.");
    }

    int bmap_blocks = Math.toIntExact(((size / (BLOCK_SIZE * 8)) + 1));
    block_count += bmap_blocks;
    block_count += Math.toIntExact(nblocks);
    if (size != block_count) {
      super_block_size_error = true;
      System.err
          .println(
              "Error <super block>: 'size' contradicts the total block count calculated from other data.");
    }

    super_block_error = super_block_inconsistent || super_block_size_error;
    /**************************************************************************/

    return "super block test: " + ((super_block_error) ? "failed" : "passed")
        + ((super_block_size_error) ? "\n\tFound size error." : "") + ((super_block_inconsistent)
            ? "\n\tFound inconsistencies with block count and block start." : "");
  }

  /**
   * ブロックの使用状況に関する一貫性のテスト結果を文字列として返す
   *
   * @return
   */
  private String bmap_test() {
    /**************************************************************************/
    // ビットマップブロックに格納されているビットマップが，各ブロックの使用/未使用を正しく表しているか。
    // 使用されている各データブロックは，ただ一つのinodeあるいは間接参照ブロックから参照されているか。
    /**************************************************************************/
    block_reference_count = new int[Math.toIntExact(size)];
    for (xv6_dinode inode : dinodes) {
      if (!inode.invalid && !inode.unused) {
        for (int k = 0; k < (NUMBER_DIRECT + 1); k++) {
          long i = inode.addrs[k];
          if (((size - nblocks - 1) < i) && (i < size)) {
            block_reference_count[Math.toIntExact(i)]++;
            if (k == NUMBER_DIRECT) {
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
    return "bmap test: " + ((bmap_error) ? "failed" : "passed")
        + ((bmap_multiple_references > 0)
            ? ("\n\tFound " + bmap_multiple_references + " inode multiple reference error.") : "")
        + ((bmap_error_unused > 0)
            ? ("\n\tFound " + bmap_error_unused + " incorrect 'unused' flag errors.") : "")
        + ((bmap_error_used > 0)
            ? ("\n\tFound " + bmap_error_used + " incorrect 'used' flag errors.") : "");
  }

  /**
   * inodeに関する一貫性のテストの結果を文字列として返す。
   *
   * @return
   */
  private String inode_test() {
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
        // T_DEV
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
      // addrs(および間接参照ブロック)が使用済みデータブロックを参照しているか。
      /**************************************************************************/
      int referenced_blocks = 0;
      for (int l = 0; l < (NUMBER_DIRECT + 1); l++) {
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
            if (l == NUMBER_DIRECT) {
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
      // nlinkが各ディレクトリからの正しい総参照数になっているか。
      /**************************************************************************/
      if (inode.unused && (inode.nlink != 0)) {
        inode.invalid = true;
        System.err.println("Error <inode      >: Unused inode has non-zero 'nlink'");
        inode_type_error++;
      } else if ((inode.type == 1) && !inode.invalid) {
        // T_DIR
        // load all dirents
        List<xv6_dirent> dirents = new ArrayList<>();
        for (int i = 0; i < (NUMBER_DIRECT + 1); i++) {
          int m = (int) inode.addrs[i];
          if ((i == NUMBER_DIRECT) && (m != 0)) {
            // indirect reference
            long[] data = get_block_as_long(i);
            for (long n : data) {
              if (n != 0) {
                dirents.addAll(get_dirents((int) n));
              }
            }
          } else if (m != 0) {
            // direct reference
            dirents.addAll(get_dirents(m));
          }
        }

        // count links
        for (xv6_dirent d : dirents) {
          int directory_reference = d.inum;
          if ((0 <= directory_reference) && (directory_reference < ninodes)) {
            dinodes[directory_reference].directory_links += 1;
          } else {
            System.err
                .println("Error <inode      >: directory inode reference out of bounds. ("
                    + directory_reference + ")");
            inode_directory_error++;
          }
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

    for (int k = 0; k < ninodes; k++) {
      xv6_dinode inode = dinodes[k];
      if (k == 1) {
        // root inode
        if (inode.nlink != 1) {
          System.err.println("Error <inode      >: Incorrect 'nlink' at root");
          inode_directory_error++;
        }
      } else if (inode.nlink != inode.directory_links) {
        System.err.println("Error <inode      >: Incorrect 'nlink'");
        inode_directory_error++;
      }
    }

    inode_error = (inode_type_error > 0) || (inode_directory_error > 0)
        || (inode_block_address_error > 0) || (inode_size_error > 0);

    return "inode test: " + ((inode_error) ? "failed" : "passed")
        + ((inode_type_error > 0) ? ("\n\tFound " + inode_type_error + " type errors.") : "")
        + ((inode_directory_error > 0)
            ? ("\n\tFound " + inode_directory_error + " directory errors.") : "")
        + ((inode_block_address_error > 0)
            ? ("\n\tFound " + inode_block_address_error + " address error.") : "")
        + ((inode_size_error > 0) ? ("\n\tFound " + inode_size_error + " size errors.") : "");
  }

  /**
   * ディレクトリに関する一貫性のテストの結果を文字列として返す
   *
   * @return
   */
  private String directory_test() {
    for (int k = 0; k < ninodes; k++) {
      xv6_dinode inode = dinodes[k];
      if (!inode.invalid && !inode.unused) {
        if (inode.type == 1) {
          // T_DIR
          // load all dirents
          List<xv6_dirent> dirents = new ArrayList<>();
          for (int i = 0; i < (NUMBER_DIRECT + 1); i++) {
            int m = (int) inode.addrs[i];
            if ((i == NUMBER_DIRECT) && (m != 0)) {
              // indirect reference
              long[] data = get_block_as_long(i);
              for (long n : data) {
                if (n != 0) {
                  dirents.addAll(get_dirents((int) n));
                }
              }
            } else if (m != 0) {
              // direct reference
              dirents.addAll(get_dirents(m));
            }
          }

          for (xv6_dirent d : dirents) {
            int directory_reference = d.inum;
            /**************************************************************************/
            // ディレクトリが参照しているのは正しい（使用済み）inode番号か。
            /**************************************************************************/
            if (dinodes[directory_reference].unused || dinodes[directory_reference].invalid) {
              System.err
                  .println("Error <directory  >: Directory referenced unused or invalid inode.");
              directory_unused_inode_error++;
            } else if ((0 <= directory_reference) && (directory_reference < ninodes)) {
              inode.dirents.add(d);
            }
            /**************************************************************************/
          }
        }
      }
    }
    /**************************************************************************/
    // ルート以外のディレクトリについて、".", ".."がそれぞれ、自分自身と親ディレクトリを指しているか。
    /**************************************************************************/
    byte[] byte_dot = {
                        (byte) 0x2E,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00 };
    byte[] byte_dot_dot = {
                            (byte) 0x2E,
                            (byte) 0x2E,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00,
                            (byte) 0x00 };
    for (int k = 0; k < ninodes; k++) {
      xv6_dinode inode = dinodes[k];
      if (!inode.invalid && !inode.unused && (inode.type == 1)) {

        boolean dot = false;
        boolean dot_dot = false;

        for (xv6_dirent d : inode.dirents) {
          if (Arrays.equals(d.name, byte_dot)) {
            // '.'
            if (d.inum != k) {
              System.err.println("Error <directory  >: '.' does not point to self.");
              directory_self_parent_error++;
            } else if (dot) {
              System.err.println("Error <directory  >: Found multiple '.'.");
              directory_self_parent_error++;
            } else {
              dot = true;
            }
          } else if (Arrays.equals(d.name, byte_dot_dot)) {
            // '..'
            if (k == 1) {
              // root directory
              if (d.inum != k) {
                System.err
                    .println(
                        "Error <directory  >: '..' does not point to self for root directory.");
                directory_self_parent_error++;
              } else if (dot_dot) {
                System.err.println("Error <directory  >: Found multiple '..'.");
                directory_self_parent_error++;
              } else {
                dot_dot = true;
              }
            } else {
              if (inode.parent_inode == 0) {
                inode.parent_inode = d.inum;
              } else {
                System.err.println("Error <directory  >: Found multiple '..'.");
                directory_self_parent_error++;
              }
            }
          } else {
            // do nothing
          }
        }
        if (!(dot && dot_dot)) {
          System.err.println("Error <directory  >: Missing '.', '..' reference.");
          directory_self_parent_error++;
          for (xv6_dirent d : inode.dirents) {
            System.err.println(d);
          }
        }
      }
    }

    for (int k = 0; k < ninodes; k++) {
      xv6_dinode inode = dinodes[k];
      if (!inode.invalid && !inode.unused && (inode.type == 1)) {
        for (xv6_dirent d : inode.dirents) {
          if (!(Arrays.equals(d.name, byte_dot) || Arrays.equals(d.name, byte_dot_dot))) {
            if ((dinodes[d.inum].parent_inode != k) && (dinodes[d.inum].type == 1)) {
              System.err.println("Error <directory  >: '..' does not point to parent.");
              directory_self_parent_error++;
            }
          }
        }
      }
    }
    /**************************************************************************/

    directory_error = (directory_unused_inode_error > 0) || (directory_self_parent_error > 0);
    return "directory test: " + ((directory_error) ? "failed" : "passed")
        + ((directory_unused_inode_error > 0)
            ? ("\n\tFound " + directory_unused_inode_error + " inode reference errors.") : "")
        + ((directory_self_parent_error > 0)
            ? ("\n\tFound " + directory_self_parent_error + " error with '.' and '..' references.")
            : "");
  }
}