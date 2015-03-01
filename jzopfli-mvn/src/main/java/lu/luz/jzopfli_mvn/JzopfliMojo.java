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


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import lu.luz.jzopfli_stream.ZopfliDeflaterOptions;
import lu.luz.jzopfli_stream.ZopfliDeflaterOptions.Strategy;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * Maven Jzopfli Plugin.
 */
@Mojo(name = "jzopfli", defaultPhase = LifecyclePhase.PACKAGE)
public class JzopfliMojo extends AbstractMojo {

	//DEFLATE///////////////////////////////////////////////

	/**
	 * DEFLATE: Whether to print more detailed output
	 */
	@Parameter(property = "jzopfli.deflate.verboseMore", defaultValue="false")
	boolean verboseMore;
	/**
	 * DEFLATE: Maximum amount of times to rerun forward and backward pass to optimize
	 * LZ77 compression cost. Good values: 10, 15 for small files, 5 for files
	 * over several MB in size or it will be too slow.
	 */
	@Parameter(property = "jzopfli.deflate.numIterations", defaultValue="15")
	int numIterations;
	/**
	 * DEFLATE: If true, splits the data in multiple deflate blocks with optimal choice
	 * for the block boundaries. Block splitting gives better compression.
	 * Default: true (1).
	 */
	@Parameter(property = "jzopfli.deflate.blockSplitting", defaultValue="true")
	boolean blockSplitting;
	/**
	 * DEFLATE: If true, chooses the optimal block split points only after doing the
	 * iterative LZ77 compression. If false, chooses the block split points
	 * first, then does iterative LZ77 on each individual block. Depending on
	 * the file, either first or last gives the best compression. Default: false
	 * (0).
	 */
	@Parameter(property = "jzopfli.deflate.blockSplittingLast", defaultValue="false")
	boolean blockSplittingLast;
	/**
	 * DEFLATE: Maximum amount of blocks to split into (0 for unlimited, but this can
	 * give extreme results that hurt compression on some files). Default value:
	 * 15.
	 */
	@Parameter(property = "jzopfli.deflate.blockSplittingMax", defaultValue="15")
	int blockSplittingMax;
	/**
	 * DEFLATE: strategy
	 */
	@Parameter(property = "jzopfli.deflate.strategy", defaultValue="ZOPFLI_DYNAMIC_TREE")
	Strategy strategy = Strategy.ZOPFLI_DYNAMIC_TREE;
	/**
	 * DEFLATE: A block structure of huge, non-smart, blocks to divide the input into, to
	 * allow operating on huge files without exceeding memory, such as the 1GB
	 * wiki9 corpus. The whole compression algorithm, including the smarter
	 * block splitting, will be executed independently on each huge block.
	 * Dividing into huge blocks hurts compression, but not much relative to the
	 * size. Set this to, for example, 20MB (20000000). Set it to 0 to disable
	 * master blocks.
	 */
	@Parameter(property = "jzopfli.deflate.masterBlockSize", defaultValue="20000000")
	int masterBlockSize;
	/**
	 * DEFLATE: The window size for deflate. Must be a power of two. This should be
	 * 32768, the maximum possible by the deflate spec. Anything less hurts
	 * compression more than speed.
	 */
	@Parameter(property = "jzopfli.deflate.windowSize", defaultValue="32768")
	int windowSize;

	//ZIP//////////////////////////////////////////////////

	/**
	 * ZIP: keepDirectories
	 */
	@Parameter(property = "jzopfli.zip.keepDirectories", defaultValue="false")
	boolean keepDirectories;
	/**
	 * ZIP: keepExtra
	 */
	@Parameter(property = "jzopfli.zip.keepExtra", defaultValue="false")
	boolean keepExtra;
	/**
	 * ZIP: keepComment
	 */
	@Parameter(property = "jzopfli.zip.keepComment", defaultValue="false")
	boolean keepComment;
	/**
	 * ZIP: keepNestedZips
	 */
	@Parameter(property = "jzopfli.zip.keepNestedZips", defaultValue="false")
	boolean keepNestedZips;

	//MAVEN////////////////////////////////////////////////

	/**
	 * MAVEN: See <a href=
	 * "http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options"
	 * >options</a>.
	 */
	@Parameter(property = "jzopfli.verbose", defaultValue = "false")
	private boolean verbose;

	/**
	 * MAVEN: Archive to process. If set, neither the project artifact nor any
	 * attachments or archive sets are processed.
	 */
	@Parameter(property = "jzopfli.archive")
	private File archive;

	/**
	 * MAVEN: The base directory to scan for JAR files using Ant-like
	 * inclusion/exclusion patterns.
	 *
	 */
	@Parameter(property = "jzopfli.archiveDirectory")
	private File archiveDirectory;

	/**
	 * MAVEN: The Ant-like inclusion patterns used to select JAR files to process. The
	 * patterns must be relative to the directory given by the parameter
	 * {@link #archiveDirectory}. By default, the pattern
	 * <code>&#42;&#42;/&#42;.?ar</code> is used.
	 *
	 */
	@Parameter
	private String[] includes = { "**/*.?ar" };

	/**
	 * MAVEN: The Ant-like exclusion patterns used to exclude JAR files from
	 * processing. The patterns must be relative to the directory given by the
	 * parameter {@link #archiveDirectory}.
	 *
	 */
	@Parameter
	private String[] excludes = {};

	/**
	 * MAVEN: Set to {@code true} to disable the plugin.
	 */
	@Parameter(property = "jzopfli.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * MAVEN: Controls processing of the main artifact produced by the project.
	 *
	 */
	@Parameter(property = "jzopfli.processMainArtifact", defaultValue = "true")
	private boolean processMainArtifact;

	/**
	 * MAVEN: Controls processing of project attachments. If enabled, attached
	 * artifacts that are no JAR/ZIP files will be automatically excluded from
	 * processing.
	 *
	 */
	@Parameter(property = "jzopfli.processAttachedArtifacts", defaultValue = "true")
	private boolean processAttachedArtifacts;

	/**
	 * MAVEN: A set of artifact classifiers describing the project attachments that
	 * should be processed. This parameter is only relevant if
	 * {@link #processAttachedArtifacts} is <code>true</code>. If empty, all
	 * attachments are included.
	 *
	 */
	@Parameter
	private String[] includeClassifiers;

	/**
	 * MAVEN: A set of artifact classifiers describing the project attachments that
	 * should not be processed. This parameter is only relevant if
	 * {@link #processAttachedArtifacts} is <code>true</code>. If empty, no
	 * attachments are excluded.
	 *
	 */
	@Parameter
	private String[] excludeClassifiers;

	/**
	 * The Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@SuppressWarnings("unchecked")
	@Override
	public final void execute() throws MojoExecutionException {
		if (!this.skip) {
			int processed = 0;

			if (this.archive != null) {
				processArchive(archive);
				processed++;
			} else {
				if (processMainArtifact)
					processed += processArtifact(this.project.getArtifact()) ? 1 : 0;

				if (processAttachedArtifacts) {
					Collection<String> includes = new HashSet<String>();
					if (includeClassifiers != null)
						includes.addAll(Arrays.asList(includeClassifiers));

					Collection<String> excludes = new HashSet<String>();
					if (excludeClassifiers != null)
						excludes.addAll(Arrays.asList(excludeClassifiers));

					for (Object o : project.getAttachedArtifacts()) {
						Artifact artifact = (Artifact) o;

						if (!includes.isEmpty() && !includes.contains(artifact.getClassifier()))
							continue;
						if (excludes.contains(artifact.getClassifier()))
							continue;

						processed += processArtifact(artifact) ? 1 : 0;
					}
				} else {
					if (verbose)
						getLog().info(getMessage("ignoringAttachments"));
					else
						getLog().debug(getMessage("ignoringAttachments"));
				}

				if (archiveDirectory != null) {
					String includeList = (includes != null) ? StringUtils.join(includes, ",") : null;
					String excludeList = (excludes != null) ? StringUtils.join(excludes, ",") : null;

					List<File> jarFiles;
					try {
						jarFiles = FileUtils.getFiles(archiveDirectory, includeList, excludeList);
					} catch (IOException e) {
						throw new MojoExecutionException("Failed to scan archive directory for JARs: " + e.getMessage(), e);
					}

					for (File jarFile : jarFiles) {
						processArchive(jarFile);
						processed++;
					}
				}
			}
			getLog().info(getMessage("processed", processed));
		} else
			getLog().info(getMessage("disabled"));
	}

	/**
	 * Checks whether the specified artifact is a ZIP file.
	 *
	 * @param artifact
	 *            The artifact to check, may be <code>null</code>.
	 * @return <code>true</code> if the artifact looks like a ZIP file,
	 *         <code>false</code> otherwise.
	 */
	private boolean isZipFile(final Artifact artifact) {
		boolean result = false;
		if (artifact != null && artifact.getFile() != null) {
			try (ZipInputStream zis = new ZipInputStream(new FileInputStream(artifact.getFile()))) {
				result = zis.getNextEntry() != null;
			} catch (Exception e) {
			}
		}
		return result;
	}

	/**
	 * Processes a given artifact.
	 *
	 * @param artifact
	 *            The artifact to process.
	 * @return <code>true</code> if the artifact is a JAR and was processed,
	 *         <code>false</code> otherwise.
	 * @throws NullPointerException
	 *             if {@code artifact} is {@code null}.
	 * @throws MojoExecutionException
	 *             if processing {@code artifact} fails.
	 */
	private boolean processArtifact(final Artifact artifact) throws MojoExecutionException {
		if (artifact == null)
			throw new NullPointerException("artifact");

		boolean processed = false;
		if (isZipFile(artifact)) {
			processArchive(artifact.getFile());
			processed = true;
		} else {
			if (this.verbose)
				getLog().info(getMessage("unsupported", artifact));
			else if (getLog().isDebugEnabled())
				getLog().debug(getMessage("unsupported", artifact));
		}
		return processed;
	}

	/**
	 * Processes a given archive.
	 *
	 * @param input
	 *            The archive to process.
	 * @throws NullPointerException
	 *             if {@code archive} is {@code null}.
	 * @throws MojoExecutionException
	 *             if processing {@code archive} fails.
	 */
	private void processArchive(final File input) throws MojoExecutionException {
		if (input == null)
			throw new NullPointerException("archive");

		if (this.verbose)
			getLog().info(getMessage("processing", input));
		else if (getLog().isDebugEnabled())
			getLog().debug(getMessage("processing", input));

		try{
			Path target=Paths.get(project.getBuild().getDirectory());
			Path tempOutput=Files.createTempFile(target, "jzopfli", null);
			ZopfliDeflaterOptions deflateOpts=new ZopfliDeflaterOptions();
			deflateOpts.setVerbose(verbose);
			deflateOpts.setVerboseMore(verboseMore);
			deflateOpts.setNumIterations(numIterations);
			deflateOpts.setBlocSplitting(blockSplitting);
			deflateOpts.setBlockSplittingLast(blockSplittingLast);
			deflateOpts.setBlockSplittingMax(blockSplittingMax);
			deflateOpts.setStrategy(strategy);
			deflateOpts.setMasterBlockSize(masterBlockSize);
			deflateOpts.setWindowSize(windowSize);
			ZipOptions zipOpts=new ZipOptions();
			zipOpts.setKeepDirectories(keepDirectories);
			zipOpts.setKeepExtra(keepExtra);
			zipOpts.setKeepComment(keepComment);
			zipOpts.setKeepNestedZips(keepNestedZips);

			try (ZipFile zip = new ZipFile(input);
					OutputStream os = Files.newOutputStream(tempOutput)) {
				ZopfliTools.recompress(zip, os, zipOpts, deflateOpts);
			}

			Path inputPath=input.toPath();
			Path relative=target.relativize(inputPath);
			long inLength = Files.size(inputPath);
			long gain = inLength - Files.size(tempOutput);
			if(gain>0){
				//write into the file. Avoid replacing or deleting it
				try(OutputStream os=Files.newOutputStream(inputPath)){
					Files.copy(tempOutput, os);
				}
				Files.delete(tempOutput);

				double ratio = (double)gain / inLength;
				getLog().info(getMessage("reduced", relative, gain, ratio, inLength));
			}else{
				getLog().info(getMessage("notReduced", relative));
			}
		} catch (Exception e) {
			throw new MojoExecutionException(getMessage("failure", e.getMessage()), e);
		}
	}

	/**
	 * Gets a message for a given key from the resource bundle backing the
	 * implementation.
	 *
	 * @param key
	 *            The key of the message to return.
	 * @param args
	 *            Arguments to format the message with or {@code null}.
	 * @return The message with key {@code key} from the resource bundle backing
	 *         the implementation.
	 * @throws NullPointerException
	 *             if {@code key} is {@code null}.
	 * @throws java.util.MissingResourceException
	 *             if there is no message available matching {@code key} or
	 *             accessing the resource bundle fails.
	 */
	private String getMessage(final String key, final Object... args) {
		if (key == null)
			throw new NullPointerException("key");
		return new MessageFormat(ResourceBundle.getBundle("jzopfli").getString(key)).format(args);
	}
}
