package com.rocwinger.android.multiapk.export;

import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import java.io.File;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

final class KeystoreSelectionPage extends ExportWizard.ExportWizardPage
{
  private final ExportWizard mWizard;
  private Button mUseExistingKeystore;
  private Button mCreateKeystore;
  private Text mKeystore;
  private Text mKeystorePassword;
  private Label mConfirmLabel;
  private Text mKeystorePassword2;
  private boolean mDisableOnChange = false;

  protected KeystoreSelectionPage(ExportWizard wizard, String pageName) {
    super(pageName);
    this.mWizard = wizard;

    setTitle("Keystore selection");
    setDescription("");
  }

  public void createControl(Composite parent)
  {
    Composite composite = new Composite(parent, 0);
    composite.setLayoutData(new GridData(1808));
    GridLayout gl = new GridLayout(3, false);
    composite.setLayout(gl);

    this.mUseExistingKeystore = new Button(composite, 16);
    this.mUseExistingKeystore.setText("Use existing keystore");
    GridData gd;
    this.mUseExistingKeystore.setLayoutData(gd = new GridData(768));
    gd.horizontalSpan = 3;
    this.mUseExistingKeystore.setSelection(true);

    this.mCreateKeystore = new Button(composite, 16);
    this.mCreateKeystore.setText("Create new keystore");
    this.mCreateKeystore.setLayoutData(gd = new GridData(768));
    gd.horizontalSpan = 3;

    new Label(composite, 0).setText("Location:");
    this.mKeystore = new Text(composite, 2048);
    this.mKeystore.setLayoutData(gd = new GridData(768));
    final Button browseButton = new Button(composite, 8);
    browseButton.setText("Browse...");
    browseButton.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e)
      {
        FileDialog fileDialog;
        if (KeystoreSelectionPage.this.mUseExistingKeystore.getSelection()) {
          fileDialog = new FileDialog(browseButton.getShell(), 4096);
          fileDialog.setText("Load Keystore");
        } else {
          fileDialog = new FileDialog(browseButton.getShell(), 8192);
          fileDialog.setText("Select Keystore Name");
        }

        String fileName = fileDialog.open();
        if (fileName != null)
          KeystoreSelectionPage.this.mKeystore.setText(fileName);
      }
    });
    new Label(composite, 0).setText("Password:");
    this.mKeystorePassword = new Text(composite, 4196352);
    this.mKeystorePassword.setLayoutData(gd = new GridData(768));
    this.mKeystorePassword.addVerifyListener(sPasswordVerifier);
    new Composite(composite, 0).setLayoutData(gd = new GridData());
    gd.heightHint = (gd.widthHint = 0);

    this.mConfirmLabel = new Label(composite, 0);
    this.mConfirmLabel.setText("Confirm:");
    this.mKeystorePassword2 = new Text(composite, 4196352);
    this.mKeystorePassword2.setLayoutData(gd = new GridData(768));
    this.mKeystorePassword2.addVerifyListener(sPasswordVerifier);
    new Composite(composite, 0).setLayoutData(gd = new GridData());
    gd.heightHint = (gd.widthHint = 0);
    this.mKeystorePassword2.setEnabled(false);

    setErrorMessage(null);
    setMessage(null);
    setControl(composite);

    this.mUseExistingKeystore.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e) {
        boolean createStore = !KeystoreSelectionPage.this.mUseExistingKeystore.getSelection();
        KeystoreSelectionPage.this.mKeystorePassword2.setEnabled(createStore);
        KeystoreSelectionPage.this.mConfirmLabel.setEnabled(createStore);
        KeystoreSelectionPage.this.mWizard.setKeystoreCreationMode(createStore);
        KeystoreSelectionPage.this.onChange();
      }
    });
    this.mKeystore.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeystoreSelectionPage.this.mWizard.setKeystore(KeystoreSelectionPage.this.mKeystore.getText().trim());
        KeystoreSelectionPage.this.onChange();
      }
    });
    this.mKeystorePassword.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeystoreSelectionPage.this.mWizard.setKeystorePassword(KeystoreSelectionPage.this.mKeystorePassword.getText());
        KeystoreSelectionPage.this.onChange();
      }
    });
    this.mKeystorePassword2.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeystoreSelectionPage.this.onChange();
      }
    });
  }

  public IWizardPage getNextPage()
  {
    if (this.mUseExistingKeystore.getSelection()) {
      return this.mWizard.getKeySelectionPage();
    }

    return this.mWizard.getKeyCreationPage();
  }

  void onShow()
  {
    if ((this.mProjectDataChanged & 0x1) != 0)
    {
      IProject project = this.mWizard.getProject();

      this.mDisableOnChange = true;

      String keystore = ProjectHelper.loadStringProperty(project, 
        "keystore");
      if (keystore != null) {
        this.mKeystore.setText(keystore);
      }

      this.mKeystorePassword.setText("");
      this.mKeystorePassword2.setText("");

      this.mDisableOnChange = false;
      onChange();
    }
  }

  private void onChange()
  {
    if (this.mDisableOnChange) {
      return;
    }

    setErrorMessage(null);
    setMessage(null);

    boolean createStore = !this.mUseExistingKeystore.getSelection();

    String keystore = this.mKeystore.getText().trim();
    if (keystore.length() == 0) {
      setErrorMessage("Enter path to keystore.");
      setPageComplete(false);
      return;
    }
    File f = new File(keystore);
    if (!f.exists()) {
      if (!createStore) {
        setErrorMessage("Keystore does not exist.");
        setPageComplete(false);
      }
    } else {
      if (f.isDirectory()) {
        setErrorMessage("Keystore path is a directory.");
        setPageComplete(false);
        return;
      }if ((f.isFile()) && 
        (createStore)) {
        setErrorMessage("File already exists.");
        setPageComplete(false);
        return;
      }

    }

    String value = this.mKeystorePassword.getText();
    if (value.length() == 0) {
      setErrorMessage("Enter keystore password.");
      setPageComplete(false);
      return;
    }if ((createStore) && (value.length() < 6)) {
      setErrorMessage("Keystore password is too short - must be at least 6 characters.");
      setPageComplete(false);
      return;
    }

    if (createStore) {
      if (this.mKeystorePassword2.getText().length() == 0) {
        setErrorMessage("Confirm keystore password.");
        setPageComplete(false);
        return;
      }

      if (!this.mKeystorePassword.getText().equals(this.mKeystorePassword2.getText())) {
        setErrorMessage("Keystore passwords do not match.");
        setPageComplete(false);
        return;
      }
    }

    setPageComplete(true);
  }
}