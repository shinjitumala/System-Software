import java.io.IOException;
import java.io.PrintWriter;

public class xv6_img_tester {
  public static void main(String[] args) {
    if (args.length != 1) {
      // test all .img files
      try {
        for (int i = 1; i < 13; i++) {
          String path = "sample\\fs" + extend(i) + ".img";
          new xv6_img_file(path);
        }
      } catch (IOException | InterruptedException e) {
        System.err.println("Error reading file!");
        e.printStackTrace();
        System.exit(1);
      }

    } else if (args[0].equals("raw")) {
      // output raw data of all .img files
      try {
        for (int i = 1; i < 13; i++) {
          String path = "sample\\fs" + extend(i) + ".img";
          String output_path = "output\\fs" + extend(i) + "_raw.txt";
          PrintWriter file = new PrintWriter(output_path);
          xv6_img_file test_file = new xv6_img_file(path);
          file.println(path + ":");
          for (int k = 0; k < 1000; k++) {
            long[] data = test_file.get_block_as_long(k);
            for (long l : data) {
              file.print(l + " ");
            }
            file.println();
          }
          file.println();
          file.close();
        }
      } catch (IOException | InterruptedException e) {
        System.err.println("Error reading file!");
        e.printStackTrace();
        System.exit(1);
      }

    } else {
      // test a specific .img file.
      try {
        new xv6_img_file(args[0]);
      } catch (IOException | InterruptedException e) {
        System.out.println("Error reading file!");
        System.exit(1);
      }
    }
  }

  public static String extend(int i) {
    if (i < 10) {
      return "0" + i;
    } else {
      return "" + i;
    }
  }
}
