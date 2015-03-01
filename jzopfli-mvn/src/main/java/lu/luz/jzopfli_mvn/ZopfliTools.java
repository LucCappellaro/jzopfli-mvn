package lu.luz.jzopfli_mvn;

/*
 * #%L
 * JZopfli Maven
 * %%
 * Copyright (C) 2015 Luc Cappellaro
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lu.luz.jzopfli_stream.ZopfliDeflaterOptions;
import lu.luz.jzopfli_stream.ZopfliOutputStream;


public final class ZopfliTools {
	private static final int BUFFER_SIZE = 8192;
	private static final String[] EXTENSIONS = {".zip", ".jar", ".ejb", ".war", ".ear", ".rar", ".par"};

	private static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<byte[]>() {
		@Override protected byte[] initialValue() {
			return new byte[BUFFER_SIZE];
		}
	};

	private ZopfliTools(){}

	public static void recompress(ZipFile zip, OutputStream os, ZipOptions zipOpts) throws IOException{
		recompress(zip, os, zipOpts, new ZopfliDeflaterOptions());
	}

	public static void recompress(ZipFile zip, OutputStream os, ZipOptions zipOpts, ZopfliDeflaterOptions deflateOpts) throws IOException{
		ZopfliOutputStream zos=new ZopfliOutputStream(os, deflateOpts);

		for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();){
			ZipEntry inEntry=e.nextElement();
			String inName=inEntry.getName();
			if(!zipOpts.isKeepDirectories() && inName.endsWith("/"))
				continue;
			InputStream zis = zip.getInputStream(inEntry);
			ZipEntry outEntry=new ZipEntry(inName);
			outEntry.setTime(inEntry.getTime());
			// outEntry.setSize(inEntry.getSize());
			// outEntry.setCrc(inEntry.getCrc());

			if(zipOpts.isKeepExtra())
				outEntry.setExtra(inEntry.getExtra());

			if(zipOpts.isKeepComment()) //Comment are unavailable in ZipInputStream (located at the end of a Zip)
				outEntry.setComment(inEntry.getComment());

			zos.putNextEntry(outEntry);
			if(!zipOpts.isKeepNestedZips() && isZip(inName))
				recompressStored(zis, zos);
			else
				copy(zis, zos);
		}
		zos.setComment("jzopfli");
		zos.finish();
	}

	private static byte[] read(ZipEntry entry, InputStream zis) throws IOException{
		byte[] result;
		if(entry.getSize()>Integer.MAX_VALUE)
			throw new IllegalStateException("Entry too large to fit in memory: "+entry);
		int read;
		if(entry.getSize()!=-1){
			result=new byte[(int)entry.getSize()];
			int offset = 0;
			while ((read = zis.read(result, offset, result.length - offset)) > 0)
				offset += read;
		}else{
			ByteArrayOutputStream bos=new ByteArrayOutputStream();
			copy(zis, bos);
			result=bos.toByteArray();
		}
		return result;
	}

	private static void copy(InputStream is, OutputStream os) throws IOException{
		int read;
		byte[] buffer = BUFFER.get();
		while ((read = is.read(buffer)) != -1)
			os.write(buffer, 0, read);
	}

	private static boolean isZip(String name){
		String lowerCase=name.toLowerCase();
		for(String extension : EXTENSIONS)
			if(lowerCase.endsWith(extension))
				return true;
		return false;
	}

	private static void recompressStored(InputStream is, OutputStream os) throws IOException{
		ZipInputStream zis=new ZipInputStream(is);
		ZipOutputStream zos=new ZipOutputStream(os);

		ZipEntry inEntry;
		while((inEntry=zis.getNextEntry())!=null){
			String inName=inEntry.getName();
			// if(!keepDirectories && inName.endsWith("/"))
			// 	continue;

			ZipEntry outEntry=new ZipEntry(inName);
			outEntry.setTime(inEntry.getTime());

			// if(keepExtra)
			//		outEntry.setExtra(inEntry.getExtra());

			// if(keepComment)
			// 	outEntry.setComment(inEntry.getComment());

			byte[] content=read(inEntry, zis);

			outEntry.setMethod(ZipEntry.STORED);
			outEntry.setSize(content.length);
			outEntry.setCompressedSize(content.length);
			CRC32 crc = new CRC32();
			crc.update(content);
			outEntry.setCrc(crc.getValue());
			zos.putNextEntry(outEntry);
			zos.write(content);
		}
		zos.finish();
	}
}
