package com.rocwinger.android.multiapk.export;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidPrintStream;
import com.android.ide.eclipse.adt.internal.build.BuildHelper;
import com.android.ide.eclipse.adt.internal.build.DexException;
import com.android.ide.eclipse.adt.internal.build.NativeLibInJarException;
import com.android.ide.eclipse.adt.internal.build.ProguardExecException;
import com.android.ide.eclipse.adt.internal.build.ProguardResultException;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.xml.AndroidManifest;

public final class ExportHelper {
	private static final String HOME_PROPERTY = "user.home";
	private static final String HOME_PROPERTY_REF = "${user.home}";
	private static final String SDK_PROPERTY_REF = "${sdk.dir}";
	private static final String TEMP_PREFIX = "android_";

	public static void exportReleaseApk(IProject project, File outputFile,
			PrivateKey key, X509Certificate certificate,
			IProgressMonitor monitor) throws CoreException {
		ProjectHelper.compileInReleaseMode(project, monitor);

		if (key == null)
			certificate = null;
		else if (certificate == null) {
			key = null;
		}

		try {
			IResource manifestResource = project
					.findMember("AndroidManifest.xml");
			if (manifestResource.getType() != 1) {
				throw new CoreException(new Status(4,
						"com.android.ide.eclipse.adt", String.format(
								"%1$s missing.",
								new Object[] { "AndroidManifest.xml" })));
			}

			IFileWrapper manifestFile = new IFileWrapper(
					(IFile) manifestResource);
			boolean debugMode = AndroidManifest.getDebuggable(manifestFile);

			AndroidPrintStream fakeStream = new AndroidPrintStream(null, null,
					new OutputStream() {
						public void write(int b) throws IOException {
						}
					});
			ProjectState projectState = Sdk.getProjectState(project);

			String forceJumboStr = projectState.getProperty("dex.force.jumbo");
			Boolean jumbo = Boolean.valueOf(forceJumboStr);

			String dexMergerStr = projectState
					.getProperty("dex.disable.merger");
			Boolean dexMerger = Boolean.valueOf(dexMergerStr);

			BuildToolInfo buildToolInfo = getBuildTools(projectState);

			BuildHelper helper = new BuildHelper(projectState, buildToolInfo,
					fakeStream, fakeStream, jumbo.booleanValue(),
					dexMerger.booleanValue(), debugMode, false, null);

			List libProjects = projectState.getFullLibraryProjects();

			File resourceFile = File.createTempFile("android_", ".ap_");
			resourceFile.deleteOnExit();

			helper.updateCrunchCache();

			IFolder androidOutputFolder = BaseProjectHelper
					.getAndroidOutputFolder(project);
			IFile mergedManifestFile = androidOutputFolder
					.getFile("AndroidManifest.xml");

			helper.packageResources(mergedManifestFile, libProjects, null, 0,
					resourceFile.getParent(), resourceFile.getName());

			File dexFile = File.createTempFile("android_", ".dex");
			dexFile.deleteOnExit();

			ProjectState state = Sdk.getProjectState(project);
			String proguardConfig = state.getProperties().getProperty(
					"proguard.config");

			boolean runProguard = false;
			List proguardConfigFiles = null;
			if ((proguardConfig != null) && (proguardConfig.length() > 0)) {
				if ((File.separatorChar != '/')
						&& (proguardConfig.indexOf('/') != -1)) {
					proguardConfig = proguardConfig.replace('/',
							File.separatorChar);
				}

				Iterable<String> paths = LintUtils.splitPath(proguardConfig);
				for (String path : paths) {
					if (path.startsWith("${sdk.dir}"))
						path = AdtPrefs.getPrefs().getOsSdkFolder()
								+ path.substring("${sdk.dir}".length());
					else if (path.startsWith("${user.home}")) {
						path = System.getProperty("user.home")
								+ path.substring("${user.home}".length());
					}
					File proguardConfigFile = new File(path);
					if (!proguardConfigFile.isAbsolute()) {
						proguardConfigFile = new File(project.getLocation()
								.toFile(), path);
					}
					if (proguardConfigFile.isFile()) {
						if (proguardConfigFiles == null) {
							proguardConfigFiles = new ArrayList();
						}
						proguardConfigFiles.add(proguardConfigFile);
						runProguard = true;
					} else {
						throw new CoreException(
								new Status(
										4,
										"com.android.ide.eclipse.adt",
										"Invalid proguard configuration file path "
												+ proguardConfigFile
												+ " does not exist or is not a regular file",
										null));
					}

				}

				if (proguardConfigFiles != null) {
					IFile proguardFile = androidOutputFolder
							.getFile("proguard.txt");
					proguardConfigFiles
							.add(proguardFile.getLocation().toFile());
				}
			}
			Collection dxInput;
			if (runProguard) {
				Collection<String> paths = helper.getCompiledCodePaths();

				File inputJar = File.createTempFile("android_", ".jar");
				inputJar.deleteOnExit();
				JarOutputStream jos = new JarOutputStream(new FileOutputStream(
						inputJar));

				List<String> jars = new ArrayList();

				for (String path : paths) {
					File root = new File(path);
					if (root.isDirectory())
						addFileToJar(jos, root, root);
					else if (root.isFile()) {
						jars.add(path);
					}
				}
				jos.close();

				File obfuscatedJar = File.createTempFile("android_", ".jar");
				obfuscatedJar.deleteOnExit();

				helper.runProguard(proguardConfigFiles, inputJar, jars,
						obfuscatedJar, new File(project.getLocation().toFile(),
								"proguard"));

				helper.setProguardOutput(obfuscatedJar.getAbsolutePath());

				dxInput = Collections.singletonList(obfuscatedJar
						.getAbsolutePath());
			} else {
				dxInput = helper.getCompiledCodePaths();
			}

			IJavaProject javaProject = JavaCore.create(project);

			helper.executeDx(javaProject, dxInput, dexFile.getAbsolutePath());

			helper.finalPackage(resourceFile.getAbsolutePath(),
					dexFile.getAbsolutePath(), outputFile.getAbsolutePath(),
					libProjects, key, certificate, null);
		} catch (CoreException e) {
			throw e;
		} catch (ProguardResultException e) {
			String msg = String.format(
					"Proguard returned with error code %d. See console",
					new Object[] { Integer.valueOf(e.getErrorCode()) });
			AdtPlugin.printErrorToConsole(project, new Object[] { msg });
			AdtPlugin.printErrorToConsole(project, e.getOutput());
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt", msg, e));
		} catch (ProguardExecException e) {
			String msg = String.format("Failed to run proguard: %s",
					new Object[] { e.getMessage() });
			AdtPlugin.printErrorToConsole(project, new Object[] { msg });
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt", msg, e));
		} catch (DuplicateFileException e) {
			String msg = String
					.format("Found duplicate file for APK: %1$s\nOrigin 1: %2$s\nOrigin 2: %3$s",
							new Object[] { e.getArchivePath(), e.getFile1(),
									e.getFile2() });
			AdtPlugin.printErrorToConsole(project, new Object[] { msg });
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt", e.getMessage(), e));
		} catch (NativeLibInJarException e) {
			String msg = e.getMessage();

			AdtPlugin.printErrorToConsole(project, new Object[] { msg });
			AdtPlugin.printErrorToConsole(project, e.getAdditionalInfo());
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt", e.getMessage(), e));
		} catch (DexException e) {
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt", e.getMessage(), e));
		} catch (ApkCreationException e) {
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt", e.getMessage(), e));
		} catch (Exception e) {
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt",
					"Failed to export application", e));
		} finally {
			ProjectHelper.buildWithDeps(project, 6, monitor);
			project.refreshLocal(2, monitor);
		}
	}

	public static BuildToolInfo getBuildTools(ProjectState projectState)
			throws CoreException {
		BuildToolInfo buildToolInfo = projectState.getBuildToolInfo();
		if (buildToolInfo == null) {
			buildToolInfo = Sdk.getCurrent().getLatestBuildTool();
		}

		if (buildToolInfo == null) {
			throw new CoreException(new Status(4,
					"com.android.ide.eclipse.adt",
					"No Build Tools installed in the SDK."));
		}
		return buildToolInfo;
	}

	public static void exportUnsignedReleaseApk(final IProject project) {
		Shell shell = Display.getCurrent().getActiveShell();

		String fileName = project.getName() + ".apk";

		FileDialog fileDialog = new FileDialog(shell, 8192);

		fileDialog.setText("Export Project");
		fileDialog.setFileName(fileName);

		final String saveLocation = fileDialog.open();
		if (saveLocation != null)
			new Job("Android Release Export") {
				protected IStatus run(IProgressMonitor monitor) {
					try {
						ExportHelper.exportReleaseApk(project, new File(
								saveLocation), null, null, monitor);

						AdtPlugin
								.displayWarning(
										"Android IDE Plug-in",
										String.format(
												"An unsigned package of the application was saved at\n%1$s\n\nBefore publishing the application you will need to:\n- Sign the application with your release key,\n- run zipalign on the signed package. ZipAlign is located in <SDK>/tools/\n\nAligning applications allows Android to use application resources\nmore efficiently.",
												new Object[] { saveLocation }));

						return Status.OK_STATUS;
					} catch (CoreException e) {
						AdtPlugin.displayError("Android IDE Plug-in", String
								.format("Error exporting application:\n\n%1$s",
										new Object[] { e.getMessage() }));
						return e.getStatus();
					}
				}
			}.schedule();
	}

	private static void addFileToJar(JarOutputStream jar, File file,
			File rootDirectory) throws IOException {
		if (file.isDirectory()) {
			if (!file.getName().equals("META-INF")) {
				for (File child : file.listFiles())
					addFileToJar(jar, child, rootDirectory);
			}
		} else if (file.isFile()) {
			String rootPath = rootDirectory.getAbsolutePath();
			String path = file.getAbsolutePath();
			path = path.substring(rootPath.length()).replace("\\", "/");
			if (path.charAt(0) == '/') {
				path = path.substring(1);
			}
			JarEntry entry = new JarEntry(path);
			entry.setTime(file.lastModified());
			jar.putNextEntry(entry);

			byte[] buffer = new byte[1024];

			BufferedInputStream bis = null;
			int count;
			try {
				bis = new BufferedInputStream(new FileInputStream(file));
				while ((count = bis.read(buffer)) != -1) {
					jar.write(buffer, 0, count);
				}
			} finally {
				if (bis != null)
					try {
						bis.close();
					} catch (IOException localIOException1) {
					}
			}
			jar.closeEntry();
		}
	}
}