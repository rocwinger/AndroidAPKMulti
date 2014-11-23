package com.rocwinger.android.multiapk.export;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.utils.FingerprintUtils;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.internal.build.DebugKeyProvider;
import com.android.sdklib.internal.build.KeystoreHelper;
import com.android.utils.GrabProcessOutput;

public final class ExportWizard extends Wizard implements IExportWizard {
	private static final String PROJECT_LOGO_LARGE = "icons/android-64.png";
	private static final String PAGE_PROJECT_CHECK = "Page_ProjectCheck";
	private static final String PAGE_KEYSTORE_SELECTION = "Page_KeystoreSelection";
	private static final String PAGE_KEY_CREATION = "Page_KeyCreation";
	private static final String PAGE_KEY_SELECTION = "Page_KeySelection";
	private static final String PAGE_KEY_CHECK = "Page_KeyCheck";
	static final String PROPERTY_KEYSTORE = "keystore";
	static final String PROPERTY_ALIAS = "alias";
	static final String PROPERTY_DESTINATION = "destination";
	static final int APK_FILE_SOURCE = 0;
	static final int APK_FILE_DEST = 1;
	static final int APK_COUNT = 2;
	private ExportWizardPage[] mPages = new ExportWizardPage[5];
	private IProject mProject;
	private String mKeystore;
	private String mKeystorePassword;
	private boolean mKeystoreCreationMode;
	private String mKeyAlias;
	private String mKeyPassword;
	private int mValidity;
	private String mDName;
	private PrivateKey mPrivateKey;
	private X509Certificate mCertificate;
	private File mDestinationFile;
	private ExportWizardPage mKeystoreSelectionPage;
	private ExportWizardPage mKeyCreationPage;
	private ExportWizardPage mKeySelectionPage;
	private ExportWizardPage mKeyCheckPage;
	private boolean mKeyCreationMode;
	private List<String> mExistingAliases;

	public ExportWizard() {
		setHelpAvailable(false);
		setWindowTitle("Export Android Application");
		setImageDescriptor();
	}

	public void addPages() {
		addPage(this.mPages[0] = new ProjectCheckPage(this, "Page_ProjectCheck"));
		addPage(this.mKeystoreSelectionPage = this.mPages[1] = new KeystoreSelectionPage(
				this, "Page_KeystoreSelection"));
		addPage(this.mKeyCreationPage = this.mPages[2] = new KeyCreationPage(
				this, "Page_KeyCreation"));
		addPage(this.mKeySelectionPage = this.mPages[3] = new KeySelectionPage(
				this, "Page_KeySelection"));
		addPage(this.mKeyCheckPage = this.mPages[4] = new KeyCheckPage(this,
				"Page_KeyCheck"));
	}

	public boolean performFinish() {
		ProjectHelper.saveStringProperty(this.mProject, "keystore",
				this.mKeystore);
		ProjectHelper
				.saveStringProperty(this.mProject, "alias", this.mKeyAlias);
		ProjectHelper.saveStringProperty(this.mProject, "destination",
				this.mDestinationFile.getAbsolutePath());

		IWorkbench workbench = PlatformUI.getWorkbench();
		final boolean[] result = new boolean[1];
		try {
			workbench.getProgressService().busyCursorWhile(
					new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor)
								throws InvocationTargetException,
								InterruptedException {
							try {
								result[0] = ExportWizard.this.doExport(monitor);
							} finally {
								monitor.done();
							}
						}
					});
		} catch (InvocationTargetException localInvocationTargetException) {
			return false;
		} catch (InterruptedException localInterruptedException) {
			return false;
		}

		return result[0];
	}

	private boolean doExport(IProgressMonitor monitor) {
		try {
			if ((this.mKeystoreCreationMode) || (this.mKeyCreationMode)) {
				final ArrayList output = new ArrayList();
				boolean createdStore = KeystoreHelper.createNewStore(
						this.mKeystore, null, this.mKeystorePassword,
						this.mKeyAlias, this.mKeyPassword, this.mDName,
						this.mValidity, new DebugKeyProvider.IKeyGenOutput() {
							public void err(String message) {
								output.add(message);
							}

							public void out(String message) {
								output.add(message);
							}
						});
				if (!createdStore) {
					displayError((String[]) output.toArray(new String[output
							.size()]));
					return false;
				}

				KeyStore keyStore = KeyStore.getInstance(KeyStore
						.getDefaultType());
				FileInputStream fis = new FileInputStream(this.mKeystore);
				keyStore.load(fis, this.mKeystorePassword.toCharArray());
				fis.close();
				KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) keyStore
						.getEntry(this.mKeyAlias,
								new KeyStore.PasswordProtection(
										this.mKeyPassword.toCharArray()));

				if (entry != null) {
					this.mPrivateKey = entry.getPrivateKey();
					this.mCertificate = ((X509Certificate) entry
							.getCertificate());

					AdtPlugin
							.printToConsole(
									this.mProject,
									new Object[] {
											String.format(
													"New keystore %s has been created.",
													new Object[] { this.mDestinationFile
															.getAbsolutePath() }),
											"Certificate fingerprints:",
											String.format(
													"  MD5 : %s",
													new Object[] { getCertMd5Fingerprint() }),
											String.format(
													"  SHA1: %s",
													new Object[] { getCertSha1Fingerprint() }) });
				} else {
					displayError(new String[] { "Could not find key" });
					return false;
				}

			}

			if ((this.mPrivateKey != null) && (this.mCertificate != null)) {
				boolean runZipAlign = false;

				ProjectState projectState = Sdk.getProjectState(this.mProject);
				BuildToolInfo buildToolInfo = ExportHelper
						.getBuildTools(projectState);

				String zipAlignPath = buildToolInfo
						.getPath(BuildToolInfo.PathId.ZIP_ALIGN);
				runZipAlign = (zipAlignPath != null)
						&& (new File(zipAlignPath).isFile());

				File apkExportFile = this.mDestinationFile;
				if (runZipAlign) {
					apkExportFile = File.createTempFile("androidExport_",
							".apk");
				}

				ExportHelper.exportReleaseApk(this.mProject, apkExportFile,
						this.mPrivateKey, this.mCertificate, monitor);

				if (runZipAlign) {
					String message = zipAlign(zipAlignPath, apkExportFile,
							this.mDestinationFile);
					if (message != null) {
						displayError(new String[] { message });
						return false;
					}
				} else {
					AdtPlugin
							.displayWarning(
									"Export Wizard",
									"The zipalign tool was not found in the SDK.\n\nPlease update to the latest SDK and re-export your application\nor run zipalign manually.\n\nAligning applications allows Android to use application resources\nmore efficiently.");
				}

				return true;
			}
		} catch (Throwable t) {
			displayError(t);
		}

		return false;
	}

	public boolean canFinish() {
		return ((this.mPrivateKey != null) && (this.mCertificate != null))
				|| (((this.mKeystoreCreationMode) || (this.mKeyCreationMode)) && (this.mDestinationFile != null));
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		Object selected = selection.getFirstElement();

		if ((selected instanceof IProject)) {
			this.mProject = ((IProject) selected);
		} else if ((selected instanceof IAdaptable)) {
			IResource r = (IResource) ((IAdaptable) selected)
					.getAdapter(IResource.class);
			if (r != null)
				this.mProject = r.getProject();
		}
	}

	ExportWizardPage getKeystoreSelectionPage() {
		return this.mKeystoreSelectionPage;
	}

	ExportWizardPage getKeyCreationPage() {
		return this.mKeyCreationPage;
	}

	ExportWizardPage getKeySelectionPage() {
		return this.mKeySelectionPage;
	}

	ExportWizardPage getKeyCheckPage() {
		return this.mKeyCheckPage;
	}

	private void setImageDescriptor() {
		ImageDescriptor desc = AdtPlugin
				.getImageDescriptor("icons/android-64.png");
		setDefaultPageImageDescriptor(desc);
	}

	IProject getProject() {
		return this.mProject;
	}

	void setProject(IProject project) {
		this.mProject = project;

		updatePageOnChange(1);
	}

	void setKeystore(String path) {
		this.mKeystore = path;
		this.mPrivateKey = null;
		this.mCertificate = null;

		updatePageOnChange(2);
	}

	String getKeystore() {
		return this.mKeystore;
	}

	void setKeystoreCreationMode(boolean createStore) {
		this.mKeystoreCreationMode = createStore;
		updatePageOnChange(2);
	}

	boolean getKeystoreCreationMode() {
		return this.mKeystoreCreationMode;
	}

	void setKeystorePassword(String password) {
		this.mKeystorePassword = password;
		this.mPrivateKey = null;
		this.mCertificate = null;

		updatePageOnChange(2);
	}

	String getKeystorePassword() {
		return this.mKeystorePassword;
	}

	void setKeyCreationMode(boolean createKey) {
		this.mKeyCreationMode = createKey;
		updatePageOnChange(4);
	}

	boolean getKeyCreationMode() {
		return this.mKeyCreationMode;
	}

	void setExistingAliases(List<String> aliases) {
		this.mExistingAliases = aliases;
	}

	List<String> getExistingAliases() {
		return this.mExistingAliases;
	}

	void setKeyAlias(String name) {
		this.mKeyAlias = name;
		this.mPrivateKey = null;
		this.mCertificate = null;

		updatePageOnChange(4);
	}

	String getKeyAlias() {
		return this.mKeyAlias;
	}

	void setKeyPassword(String password) {
		this.mKeyPassword = password;
		this.mPrivateKey = null;
		this.mCertificate = null;

		updatePageOnChange(4);
	}

	String getKeyPassword() {
		return this.mKeyPassword;
	}

	void setValidity(int validity) {
		this.mValidity = validity;
		updatePageOnChange(4);
	}

	int getValidity() {
		return this.mValidity;
	}

	void setDName(String dName) {
		this.mDName = dName;
		updatePageOnChange(4);
	}

	String getDName() {
		return this.mDName;
	}

	String getCertSha1Fingerprint() {
		return FingerprintUtils.getFingerprint(this.mCertificate, "SHA1");
	}

	String getCertMd5Fingerprint() {
		return FingerprintUtils.getFingerprint(this.mCertificate, "MD5");
	}

	void setSigningInfo(PrivateKey privateKey, X509Certificate certificate) {
		this.mPrivateKey = privateKey;
		this.mCertificate = certificate;
	}

	void setDestination(File destinationFile) {
		this.mDestinationFile = destinationFile;
	}

	void resetDestination() {
		this.mDestinationFile = null;
	}

	void updatePageOnChange(int changeMask) {
		for (ExportWizardPage page : this.mPages)
			page.projectDataChanged(changeMask);
	}

	private void displayError(String[] messages) {
		String message = null;
		if (messages.length == 1) {
			message = messages[0];
		} else {
			StringBuilder sb = new StringBuilder(messages[0]);
			for (int i = 1; i < messages.length; i++) {
				sb.append('\n');
				sb.append(messages[i]);
			}

			message = sb.toString();
		}

		AdtPlugin.displayError("Export Wizard", message);
	}

	private void displayError(Throwable t) {
		String message = getExceptionMessage(t);
		displayError(new String[] { message });

		AdtPlugin.log(t, "Export Wizard Error", new Object[0]);
	}

	private String zipAlign(String zipAlignPath, File source, File destination)
			throws IOException {
		String[] command = new String[5];
		command[0] = zipAlignPath;
		command[1] = "-f";
		command[2] = "4";
		command[3] = source.getAbsolutePath();
		command[4] = destination.getAbsolutePath();

		Process process = Runtime.getRuntime().exec(command);
		final ArrayList<String> output = new ArrayList();
		try {
			final IProject project = getProject();

			int status = GrabProcessOutput.grabProcessOutput(process,
					GrabProcessOutput.Wait.WAIT_FOR_READERS,
					new GrabProcessOutput.IProcessOutput() {
						public void out(@Nullable String line) {
							if (line != null)
								AdtPlugin.printBuildToConsole(
										AdtPrefs.BuildVerbosity.VERBOSE,
										project, new Object[] { line });
						}

						public void err(@Nullable String line) {
							if (line != null)
								output.add(line);
						}
					});
			if (status != 0) {
				StringBuilder sb = new StringBuilder(
						"Error while running zipalign:");
				for (String msg : output) {
					sb.append('\n');
					sb.append(msg);
				}

				return sb.toString();
			}
		} catch (InterruptedException localInterruptedException) {
		}
		return null;
	}

	static String getExceptionMessage(Throwable t) {
		String message = t.getMessage();
		if (message == null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			t.printStackTrace(new PrintStream(baos));
			message = baos.toString();
		}

		return message;
	}

	static abstract class ExportWizardPage extends WizardPage {
		protected static final int DATA_PROJECT = 1;
		protected static final int DATA_KEYSTORE = 2;
		protected static final int DATA_KEY = 4;
		protected static final VerifyListener sPasswordVerifier = new VerifyListener() {
			public void verifyText(VerifyEvent e) {
				int len = e.text.length();

				if (len + ((Text) e.getSource()).getText().length() > 127) {
					e.doit = false;
					return;
				}

				for (int i = 0; i < len; i++)
					if (e.text.charAt(i) < ' ') {
						e.doit = false;
						return;
					}
			}
		};

		protected int mProjectDataChanged = 0;

		ExportWizardPage(String name) {
			super(name);
		}

		abstract void onShow();

		public void setVisible(boolean visible) {
			super.setVisible(visible);
			if (visible) {
				onShow();
				this.mProjectDataChanged = 0;
			}
		}

		final void projectDataChanged(int changeMask) {
			this.mProjectDataChanged |= changeMask;
		}

		protected void onException(Throwable t) {
			String message = ExportWizard.getExceptionMessage(t);

			setErrorMessage(message);
			setPageComplete(false);
		}
	}
}