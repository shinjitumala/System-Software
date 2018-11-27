import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class xv6_img_tester {
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("Usage: java xv6_img_tester <path to xv6 img file>");
      System.exit(1);
    }

    byte[] bytes = null;
    try {
      File file = new File(args[0]);
      FileInputStream fis = new FileInputStream(file);
      bytes = new byte[(int)file.length()];
      fis.read(bytes);

      ByteBuffer bb = ByteBuffer.wrap(bytes);
      bb.order(ByteOrder.LITTLE_ENDIAN);

      System.out.println(bb.getInt());
    } catch (IOException e) {
      System.out.println("Error reading file!");
      System.exit(1);
    }
    // int i = 0;
    // for (byte b : bytes) {
    //   System.out.print(0xff & b);
    //   if(++i == 512){
    //     System.out.println("");
    //     i = 0;
    //   }else{
    //     System.out.print(" ");
    //   }
    // }
  }
}
