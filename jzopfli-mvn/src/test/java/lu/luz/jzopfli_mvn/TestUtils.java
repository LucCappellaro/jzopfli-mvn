package lu.luz.jzopfli_mvn;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

public class TestUtils {

	private TestUtils(){}

	public static byte[] decompressEntry(ZipInputStream zis) throws IOException {
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		zis.getNextEntry();
		byte[] buffer=new byte[1024];
		int read;
		while((read=zis.read(buffer))!=-1)
			bos.write(buffer, 0, read);
		return bos.toByteArray();
	}

	public static byte[] newByteArray(int length) {
		byte[] array = new byte[length];
		try(FileInputStream fis=new FileInputStream("src/test/resources/1musk10.txt")){
			fis.read(array);
		}catch(IOException e){
		}
		return array;
	}
}
