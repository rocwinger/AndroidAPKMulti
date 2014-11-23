package com.rocwinger.android.multiapk.export;

import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormText;

final class KeyCheckPage extends ExportWizard.ExportWizardPage
{
  private static final int REQUIRED_YEARS = 25;
  private static final String VALIDITY_WARNING = "<p>Make sure the certificate is valid for the planned lifetime of the product.</p><p>If the certificate expires, you will be forced to sign your application with a different one.</p><p>Applications cannot be upgraded if their certificate changes from one version to another, forcing a full uninstall/install, which will make the user lose his/her data.</p><p>Google Play(Android Market) currently requires certificates to be valid until 2033.</p>";
  private final ExportWizard mWizard;
  private PrivateKey mPrivateKey;
  private X509Certificate mCertificate;
  private Text mDestination;
  private boolean mFatalSigningError;
  private FormText mDetailText;
  private ScrolledComposite mScrolledComposite;
  private String mKeyDetails;
  private String mDestinationDetails;

  protected KeyCheckPage(ExportWizard wizard, String pageName)
  {
    super(pageName);
    this.mWizard = wizard;

    setTitle("Destination and key/certificate checks");
    setDescription("");
  }

  public void createControl(Composite parent)
  {
    setErrorMessage(null);
    setMessage(null);

    Composite composite = new Composite(parent, 0);
    composite.setLayoutData(new GridData(1808));
    GridLayout gl = new GridLayout(3, false);
    gl.verticalSpacing *= 3;
    composite.setLayout(gl);

    new Label(composite, 0).setText("Destination APK file:");
    this.mDestination = new Text(composite, 2048);
    GridData gd;
    this.mDestination.setLayoutData(gd = new GridData(768));
    this.mDestination.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeyCheckPage.this.onDestinationChange(false);
      }
    });
    final Button browseButton = new Button(composite, 8);
    browseButton.setText("Browse...");
    browseButton.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e) {
        FileDialog fileDialog = new FileDialog(browseButton.getShell(), 8192);

        fileDialog.setText("Destination file name");

        String filename = ProjectHelper.getApkFilename(KeyCheckPage.this.mWizard.getProject(), 
          null);
        fileDialog.setFileName(filename);

        String saveLocation = fileDialog.open();
        if (saveLocation != null)
          KeyCheckPage.this.mDestination.setText(saveLocation);
      }
    });
    this.mScrolledComposite = new ScrolledComposite(composite, 512);
    this.mScrolledComposite.setLayoutData(gd = new GridData(1808));
    gd.horizontalSpan = 3;
    this.mScrolledComposite.setExpandHorizontal(true);
    this.mScrolledComposite.setExpandVertical(true);

    this.mDetailText = new FormText(this.mScrolledComposite, 0);
    this.mScrolledComposite.setContent(this.mDetailText);

    this.mScrolledComposite.addControlListener(new ControlAdapter()
    {
      public void controlResized(ControlEvent e) {
        KeyCheckPage.this.updateScrolling();
      }
    });
    setControl(composite);
  }

  void onShow()
  {
    if ((this.mProjectDataChanged & 0x1) != 0)
    {
      IProject project = this.mWizard.getProject();

      String destination = ProjectHelper.loadStringProperty(project, 
        "destination");
      if (destination != null) {
        this.mDestination.setText(destination);
      }

    }

    if (this.mProjectDataChanged != 0) {
      this.mFatalSigningError = false;

      this.mWizard.setSigningInfo(null, null);
      this.mPrivateKey = null;
      this.mCertificate = null;
      this.mKeyDetails = null;

      if ((this.mWizard.getKeystoreCreationMode()) || (this.mWizard.getKeyCreationMode())) {
        int validity = this.mWizard.getValidity();
        StringBuilder sb = new StringBuilder(
          String.format("<p>Certificate expires in %d years.</p>", new Object[] { 
          Integer.valueOf(validity) }));

        if (validity < 25) {
          sb.append("<p>Make sure the certificate is valid for the planned lifetime of the product.</p><p>If the certificate expires, you will be forced to sign your application with a different one.</p><p>Applications cannot be upgraded if their certificate changes from one version to another, forcing a full uninstall/install, which will make the user lose his/her data.</p><p>Google Play(Android Market) currently requires certificates to be valid until 2033.</p>");
        }

        this.mKeyDetails = sb.toString();
      } else {
        try {
          KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
          FileInputStream fis = new FileInputStream(this.mWizard.getKeystore());
          keyStore.load(fis, this.mWizard.getKeystorePassword().toCharArray());
          fis.close();
          KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(
            this.mWizard.getKeyAlias(), 
            new KeyStore.PasswordProtection(
            this.mWizard.getKeyPassword().toCharArray()));

          if (entry != null) {
            this.mPrivateKey = entry.getPrivateKey();
            this.mCertificate = ((X509Certificate)entry.getCertificate());
          } else {
            setErrorMessage("Unable to find key.");

            setPageComplete(false);
          }
        }
        catch (FileNotFoundException e)
        {
          onException(e);
        } catch (KeyStoreException e) {
          onException(e);
        } catch (NoSuchAlgorithmException e) {
          onException(e);
        } catch (UnrecoverableEntryException e) {
          onException(e);
        } catch (CertificateException e) {
          onException(e);
        } catch (IOException e) {
          onException(e);
        }

        if ((this.mPrivateKey != null) && (this.mCertificate != null)) {
          Calendar expirationCalendar = Calendar.getInstance();
          expirationCalendar.setTime(this.mCertificate.getNotAfter());
          Calendar today = Calendar.getInstance();

          if (expirationCalendar.before(today)) {
            this.mKeyDetails = String.format(
              "<p>Certificate expired on %s</p>", new Object[] { 
              this.mCertificate.getNotAfter().toString() });

            this.mFatalSigningError = true;

            setErrorMessage("Certificate is expired.");
            setPageComplete(false);
          }
          else {
            this.mWizard.setSigningInfo(this.mPrivateKey, this.mCertificate);

            StringBuilder sb = new StringBuilder(String.format(
              "<p>Certificate expires on %s.</p>", new Object[] { 
              this.mCertificate.getNotAfter().toString() }));

            int expirationYear = expirationCalendar.get(1);
            int thisYear = today.get(1);

            if (thisYear + 25 >= expirationYear)
            {
              if (expirationYear == thisYear) {
                sb.append("<p>The certificate expires this year.</p>");
              } else {
                int count = expirationYear - thisYear;
                sb.append(String.format(
                  "<p>The Certificate expires in %1$s %2$s.</p>", new Object[] { 
                  Integer.valueOf(count), count == 1 ? "year" : "years" }));
              }
              sb.append("<p>Make sure the certificate is valid for the planned lifetime of the product.</p><p>If the certificate expires, you will be forced to sign your application with a different one.</p><p>Applications cannot be upgraded if their certificate changes from one version to another, forcing a full uninstall/install, which will make the user lose his/her data.</p><p>Google Play(Android Market) currently requires certificates to be valid until 2033.</p>");
            }

            String sha1 = this.mWizard.getCertSha1Fingerprint();
            String md5 = this.mWizard.getCertMd5Fingerprint();

            sb.append("<p></p>");
            sb.append("<p>Certificate fingerprints:</p>");
            sb.append(String.format("<li>MD5 : %s</li>", new Object[] { md5 }));
            sb.append(String.format("<li>SHA1: %s</li>", new Object[] { sha1 }));
            sb.append("<p></p>");

            this.mKeyDetails = sb.toString();
          }
        }
        else {
          this.mFatalSigningError = true;
        }
      }
    }

    onDestinationChange(true);
  }

  private void onDestinationChange(boolean forceDetailUpdate)
  {
    if (!this.mFatalSigningError)
    {
      setErrorMessage(null);
      setMessage(null);

      String path = this.mDestination.getText().trim();

      if (path.length() == 0) {
        setErrorMessage("Enter destination for the APK file.");

        this.mWizard.resetDestination();
        setPageComplete(false);
        return;
      }

      File file = new File(path);
      if (file.isDirectory()) {
        setErrorMessage("Destination is a directory.");

        this.mWizard.resetDestination();
        setPageComplete(false);
        return;
      }

      File parentFolder = file.getParentFile();
      if ((parentFolder == null) || (!parentFolder.isDirectory())) {
        setErrorMessage("Not a valid directory.");

        this.mWizard.resetDestination();
        setPageComplete(false);
        return;
      }

      if (file.isFile()) {
        this.mDestinationDetails = "<li>WARNING: destination file already exists</li>";
        setMessage("Destination file already exists.", 2);
      }

      this.mWizard.setDestination(file);
      setPageComplete(true);

      updateDetailText();
    } else if (forceDetailUpdate) {
      updateDetailText();
    }
  }

  private void updateScrolling()
  {
    if (this.mDetailText != null) {
      Rectangle r = this.mScrolledComposite.getClientArea();
      this.mScrolledComposite.setMinSize(this.mDetailText.computeSize(r.width, -1));
      this.mScrolledComposite.layout();
    }
  }

  private void updateDetailText() {
    StringBuilder sb = new StringBuilder("<form>");
    if (this.mKeyDetails != null) {
      sb.append(this.mKeyDetails);
    }

    if ((this.mDestinationDetails != null) && (!this.mFatalSigningError)) {
      sb.append(this.mDestinationDetails);
    }

    sb.append("</form>");

    this.mDetailText.setText(sb.toString(), true, 
      true);

    this.mDetailText.getParent().layout();

    updateScrolling();
  }

  protected void onException(Throwable t)
  {
    super.onException(t);

    this.mKeyDetails = String.format("ERROR: %1$s", new Object[] { ExportWizard.getExceptionMessage(t) });
  }
}