package com.rocwinger.android.multiapk.export;

import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import java.util.List;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

final class KeyCreationPage extends ExportWizard.ExportWizardPage
{
  private final ExportWizard mWizard;
  private Text mAlias;
  private Text mKeyPassword;
  private Text mKeyPassword2;
  private Text mCnField;
  private boolean mDisableOnChange = false;
  private Text mOuField;
  private Text mOField;
  private Text mLField;
  private Text mStField;
  private Text mCField;
  private String mDName;
  private int mValidity = 0;
  private List<String> mExistingAliases;

  protected KeyCreationPage(ExportWizard wizard, String pageName)
  {
    super(pageName);
    this.mWizard = wizard;

    setTitle("Key Creation");
    setDescription("");
  }

  public void createControl(Composite parent)
  {
    Composite composite = new Composite(parent, 0);
    composite.setLayoutData(new GridData(1808));
    GridLayout gl = new GridLayout(2, false);
    composite.setLayout(gl);

    new Label(composite, 0).setText("Alias:");
    this.mAlias = new Text(composite, 2048);
    GridData gd;
    this.mAlias.setLayoutData(gd = new GridData(768));

    new Label(composite, 0).setText("Password:");
    this.mKeyPassword = new Text(composite, 4196352);
    this.mKeyPassword.setLayoutData(gd = new GridData(768));
    this.mKeyPassword.addVerifyListener(sPasswordVerifier);

    new Label(composite, 0).setText("Confirm:");
    this.mKeyPassword2 = new Text(composite, 4196352);
    this.mKeyPassword2.setLayoutData(gd = new GridData(768));
    this.mKeyPassword2.addVerifyListener(sPasswordVerifier);

    new Label(composite, 0).setText("Validity (years):");
    final Text validityText = new Text(composite, 2048);
    validityText.setLayoutData(gd = new GridData(768));
    validityText.addVerifyListener(new VerifyListener()
    {
      public void verifyText(VerifyEvent e)
      {
        for (int i = 0; i < e.text.length(); i++) {
          char letter = e.text.charAt(i);
          if ((letter < '0') || (letter > '9')) {
            e.doit = false;
            return;
          }
        }
      }
    });
    new Label(composite, 258).setLayoutData(
      gd = new GridData(768));
    gd.horizontalSpan = 2;

    new Label(composite, 0).setText("First and Last Name:");
    this.mCnField = new Text(composite, 2048);
    this.mCnField.setLayoutData(gd = new GridData(768));

    new Label(composite, 0).setText("Organizational Unit:");
    this.mOuField = new Text(composite, 2048);
    this.mOuField.setLayoutData(gd = new GridData(768));

    new Label(composite, 0).setText("Organization:");
    this.mOField = new Text(composite, 2048);
    this.mOField.setLayoutData(gd = new GridData(768));

    new Label(composite, 0).setText("City or Locality:");
    this.mLField = new Text(composite, 2048);
    this.mLField.setLayoutData(gd = new GridData(768));

    new Label(composite, 0).setText("State or Province:");
    this.mStField = new Text(composite, 2048);
    this.mStField.setLayoutData(gd = new GridData(768));

    new Label(composite, 0).setText("Country Code (XX):");
    this.mCField = new Text(composite, 2048);
    this.mCField.setLayoutData(gd = new GridData(768));

    setErrorMessage(null);
    setMessage(null);
    setControl(composite);

    this.mAlias.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeyCreationPage.this.mWizard.setKeyAlias(KeyCreationPage.this.mAlias.getText().trim());
        KeyCreationPage.this.onChange();
      }
    });
    this.mKeyPassword.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeyCreationPage.this.mWizard.setKeyPassword(KeyCreationPage.this.mKeyPassword.getText());
        KeyCreationPage.this.onChange();
      }
    });
    this.mKeyPassword2.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeyCreationPage.this.onChange();
      }
    });
    validityText.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        try {
          KeyCreationPage.this.mValidity = Integer.parseInt(validityText.getText());
        }
        catch (NumberFormatException localNumberFormatException) {
          KeyCreationPage.this.mValidity = 0;
        }
        KeyCreationPage.this.mWizard.setValidity(KeyCreationPage.this.mValidity);
        KeyCreationPage.this.onChange();
      }
    });
    ModifyListener dNameListener = new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeyCreationPage.this.onDNameChange();
      }
    };
    this.mCnField.addModifyListener(dNameListener);
    this.mOuField.addModifyListener(dNameListener);
    this.mOField.addModifyListener(dNameListener);
    this.mLField.addModifyListener(dNameListener);
    this.mStField.addModifyListener(dNameListener);
    this.mCField.addModifyListener(dNameListener);
  }

  void onShow()
  {
    if ((this.mProjectDataChanged & 0x3) != 0)
    {
      IProject project = this.mWizard.getProject();

      this.mDisableOnChange = true;

      String alias = ProjectHelper.loadStringProperty(project, "alias");
      if (alias != null) {
        this.mAlias.setText(alias);
      }

      if (this.mWizard.getKeyCreationMode())
        this.mExistingAliases = this.mWizard.getExistingAliases();
      else {
        this.mExistingAliases = null;
      }

      this.mKeyPassword.setText("");
      this.mKeyPassword2.setText("");

      this.mDisableOnChange = false;
      onChange();
    }
  }

  public IWizardPage getPreviousPage()
  {
    if (this.mWizard.getKeyCreationMode()) {
      return this.mWizard.getKeySelectionPage();
    }

    return this.mWizard.getKeystoreSelectionPage();
  }

  public IWizardPage getNextPage()
  {
    return this.mWizard.getKeyCheckPage();
  }

  private void onChange()
  {
    if (this.mDisableOnChange) {
      return;
    }

    setErrorMessage(null);
    setMessage(null);

    if (this.mAlias.getText().trim().length() == 0) {
      setErrorMessage("Enter key alias.");
      setPageComplete(false);
      return;
    }if (this.mExistingAliases != null)
    {
      String keyAlias = this.mAlias.getText().trim();
      for (String alias : this.mExistingAliases) {
        if (alias.equalsIgnoreCase(keyAlias)) {
          setErrorMessage("Key alias already exists in keystore.");
          setPageComplete(false);
          return;
        }
      }
    }

    String value = this.mKeyPassword.getText();
    if (value.length() == 0) {
      setErrorMessage("Enter key password.");
      setPageComplete(false);
      return;
    }if (value.length() < 6) {
      setErrorMessage("Key password is too short - must be at least 6 characters.");
      setPageComplete(false);
      return;
    }

    if (!value.equals(this.mKeyPassword2.getText())) {
      setErrorMessage("Key passwords don't match.");
      setPageComplete(false);
      return;
    }

    if (this.mValidity == 0) {
      setErrorMessage("Key certificate validity is required.");
      setPageComplete(false);
      return;
    }if (this.mValidity < 25) {
      setMessage("A 25 year certificate validity is recommended.", 2);
    } else if (this.mValidity > 1000) {
      setErrorMessage("Key certificate validity must be between 1 and 1000 years.");
      setPageComplete(false);
      return;
    }

    if ((this.mDName == null) || (this.mDName.length() == 0)) {
      setErrorMessage("At least one Certificate issuer field is required to be non-empty.");
      setPageComplete(false);
      return;
    }

    setPageComplete(true);
  }

  private void onDNameChange()
  {
    StringBuilder sb = new StringBuilder();

    buildDName("CN", this.mCnField, sb);
    buildDName("OU", this.mOuField, sb);
    buildDName("O", this.mOField, sb);
    buildDName("L", this.mLField, sb);
    buildDName("ST", this.mStField, sb);
    buildDName("C", this.mCField, sb);

    this.mDName = sb.toString();
    this.mWizard.setDName(this.mDName);

    onChange();
  }

  private void buildDName(String prefix, Text textField, StringBuilder sb)
  {
    if (textField != null) {
      String value = textField.getText().trim();
      if (value.length() > 0) {
        if (sb.length() > 0) {
          sb.append(",");
        }

        sb.append(prefix);
        sb.append('=');
        sb.append(value);
      }
    }
  }
}