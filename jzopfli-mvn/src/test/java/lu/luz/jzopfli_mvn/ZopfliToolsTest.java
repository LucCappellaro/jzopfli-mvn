package lu.luz.jzopfli_mvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class ZopfliToolsTest
{
	private static final File test=new File("src/test/resources/test.zip");
	private static final File zopfli=new File("src/test/resources/zopfli.zip");
	private static byte[] entry1Data=TestUtils.newByteArray(250);
	private static byte[] entry2Data=TestUtils.newByteArray(500);
	private static byte[] entry3Data=TestUtils.newByteArray(750);

	private static String entry1Name="data1.txt";
	private static String entry2Name="folder/data2.txt";
	private static String entry3Name="folder/subfolder/data3.txt";
	private static String entry4Name="data4.zip";

	public static void main(String[] args) throws Exception  {
		ZipOutputStream zos=new ZipOutputStream(new FileOutputStream(test));
		ZipEntry entry1=new ZipEntry(entry1Name);
		entry1.setComment("comment1");
		zos.putNextEntry(entry1);
		zos.write(entry1Data);

		ZipEntry entry2Folder=new ZipEntry(entry2Name.substring(0, entry2Name.lastIndexOf('/')+1));
		zos.putNextEntry(entry2Folder);
		ZipEntry entry2=new ZipEntry(entry2Name);
		entry2.setExtra(new byte[16]);
		zos.putNextEntry(entry2);
		zos.write(entry2Data);

		ZipEntry entry3SubFolder=new ZipEntry(entry3Name.substring(0, entry3Name.lastIndexOf('/')+1));
		zos.putNextEntry(entry3SubFolder);
		ZipEntry entry3=new ZipEntry(entry3Name);
		entry3.setMethod(ZipEntry.STORED);
		entry3.setSize(entry3Data.length);
		entry3.setCompressedSize(entry3Data.length);
		CRC32 crc = new CRC32();
		crc.update(entry3Data);
		entry3.setCrc(crc.getValue());
		zos.putNextEntry(entry3);
		zos.write(entry3Data);

		ZipEntry entry4=new ZipEntry(entry4Name);
		zos.putNextEntry(entry4);
		ZipOutputStream entry4Zos=new ZipOutputStream(zos);
		ZipEntry entry41=new ZipEntry(entry1Name);
		entry4Zos.putNextEntry(entry41);
		entry4Zos.write(entry1Data);
		ZipEntry entry42=new ZipEntry(entry2Name);
		entry4Zos.putNextEntry(entry42);
		entry4Zos.write(entry2Data);
		entry4Zos.close();

		zos.close();


		try(ZipFile zis=new ZipFile(test);
				OutputStream os=new FileOutputStream(zopfli)){
			ZopfliTools.recompress(zis, os, new ZipOptions(true, true, true, true));
		}
	}

	@Test
	public void testData() throws Exception {
		byte[] zopfli;
		try(ZipFile zis=new ZipFile(test);
				ByteArrayOutputStream bos2=new ByteArrayOutputStream();	){
			ZopfliTools.recompress(zis, bos2, new ZipOptions(true, true, true, true));
			zopfli= bos2.toByteArray();
		}

		ZipInputStream zis=new ZipInputStream(new ByteArrayInputStream(zopfli));
		assertArrayEquals(entry1Data,TestUtils.decompressEntry(zis));
		zis.getNextEntry(); //skip folder
		assertArrayEquals(entry2Data,TestUtils.decompressEntry(zis));
		zis.getNextEntry(); //skip sub folder
		assertArrayEquals(entry3Data,TestUtils.decompressEntry(zis));
	}

	@Test
	public void testRemoveDirectories() throws Exception {
		byte[] recompress;
		try(ZipFile zis=new ZipFile(zopfli);
				ByteArrayOutputStream bos2=new ByteArrayOutputStream();	){
			ZopfliTools.recompress(zis, bos2, new ZipOptions(false, true, true, true));
			recompress= bos2.toByteArray();
		}
		assertTrue(recompress.length<zopfli.length());
	}

	@Test
	public void testRemoveExtra() throws Exception {
		byte[] recompress;
		try(ZipFile zis=new ZipFile(zopfli);
				ByteArrayOutputStream bos2=new ByteArrayOutputStream();	){
			ZopfliTools.recompress(zis, bos2, new ZipOptions(true, false, true, true));
			recompress= bos2.toByteArray();
		}
		assertTrue(recompress.length<zopfli.length());
	}

	@Test
	public void testRemoveComment() throws Exception {
		byte[] recompress;
		try(ZipFile zis=new ZipFile(zopfli);
				ByteArrayOutputStream bos2=new ByteArrayOutputStream();	){
			ZopfliTools.recompress(zis, bos2, new ZipOptions(true, true, false, true));
			recompress= bos2.toByteArray();
		}
		assertTrue(recompress.length<zopfli.length());
	}

	@Test
	public void testOptimizeNestedZips() throws Exception {
		byte[] recompress;
		try(ZipFile zis=new ZipFile(zopfli);
				ByteArrayOutputStream bos2=new ByteArrayOutputStream();	){
			ZopfliTools.recompress(zis, bos2, new ZipOptions(true, true, true, false));
			recompress= bos2.toByteArray();
		}
		//		Files.write(Paths.get("OptimizeNestedZips.zip"), recompress);
		assertTrue(recompress.length<zopfli.length());
	}
}
