package com.rocwinger.android.multiapk.export;

import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Enumeration;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

final class KeySelectionPage extends ExportWizard.ExportWizardPage
{
  private final ExportWizard mWizard;
  private Label mKeyAliasesLabel;
  private Combo mKeyAliases;
  private Label mKeyPasswordLabel;
  private Text mKeyPassword;
  private boolean mDisableOnChange = false;
  private Button mUseExistingKey;
  private Button mCreateKey;

  protected KeySelectionPage(ExportWizard wizard, String pageName)
  {
    super(pageName);
    this.mWizard = wizard;

    setTitle("Key alias selection");
    setDescription("");
  }

  public void createControl(Composite parent)
  {
    Composite composite = new Composite(parent, 0);
    composite.setLayoutData(new GridData(1808));
    GridLayout gl = new GridLayout(3, false);
    composite.setLayout(gl);

    this.mUseExistingKey = new Button(composite, 16);
    this.mUseExistingKey.setText("Use existing key");
    GridData gd;
    this.mUseExistingKey.setLayoutData(gd = new GridData(768));
    gd.horizontalSpan = 3;
    this.mUseExistingKey.setSelection(true);

    new Composite(composite, 0).setLayoutData(gd = new GridData());
    gd.heightHint = 0;
    gd.widthHint = 50;
    this.mKeyAliasesLabel = new Label(composite, 0);
    this.mKeyAliasesLabel.setText("Alias:");
    this.mKeyAliases = new Combo(composite, 8);
    this.mKeyAliases.setLayoutData(new GridData(768));

    new Composite(composite, 0).setLayoutData(gd = new GridData());
    gd.heightHint = 0;
    gd.widthHint = 50;
    this.mKeyPasswordLabel = new Label(composite, 0);
    this.mKeyPasswordLabel.setText("Password:");
    this.mKeyPassword = new Text(composite, 4196352);
    this.mKeyPassword.setLayoutData(new GridData(768));

    this.mCreateKey = new Button(composite, 16);
    this.mCreateKey.setText("Create new key");
    this.mCreateKey.setLayoutData(gd = new GridData(768));
    gd.horizontalSpan = 3;

    setErrorMessage(null);
    setMessage(null);
    setControl(composite);

    this.mUseExistingKey.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e) {
        KeySelectionPage.this.mWizard.setKeyCreationMode(!KeySelectionPage.this.mUseExistingKey.getSelection());
        KeySelectionPage.this.enableWidgets();
        KeySelectionPage.this.onChange();
      }
    });
    this.mKeyAliases.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e) {
        KeySelectionPage.this.mWizard.setKeyAlias(KeySelectionPage.this.mKeyAliases.getItem(KeySelectionPage.this.mKeyAliases.getSelectionIndex()));
        KeySelectionPage.this.onChange();
      }
    });
    this.mKeyPassword.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        KeySelectionPage.this.mWizard.setKeyPassword(KeySelectionPage.this.mKeyPassword.getText());
        KeySelectionPage.this.onChange();
      }
    });
  }

  void onShow()
  {
    if ((this.mProjectDataChanged & 0x3) != 0)
    {
      this.mDisableOnChange = true;
      try
      {
        this.mWizard.setKeyCreationMode(false);
        this.mUseExistingKey.setSelection(true);
        this.mCreateKey.setSelection(false);
        enableWidgets();

        this.mKeyAliases.removeAll();

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream fis = new FileInputStream(this.mWizard.getKeystore());
        keyStore.load(fis, this.mWizard.getKeystorePassword().toCharArray());
        fis.close();

        Enumeration aliases = keyStore.aliases();

        IProject project = this.mWizard.getProject();

        String keyAlias = ProjectHelper.loadStringProperty(project, 
          "alias");

        ArrayList aliasList = new ArrayList();

        int selection = -1;
        int count = 0;
        while (aliases.hasMoreElements()) {
          String alias = (String)aliases.nextElement();
          this.mKeyAliases.add(alias);
          aliasList.add(alias);
          if ((selection == -1) && (alias.equalsIgnoreCase(keyAlias))) {
            selection = count;
          }
          count++;
        }

        this.mWizard.setExistingAliases(aliasList);

        if (selection != -1) {
          this.mKeyAliases.select(selection);

          this.mWizard.setKeyAlias(keyAlias);
        } else {
          this.mKeyAliases.clearSelection();
        }

        this.mKeyPassword.setText("");

        this.mDisableOnChange = false;
        onChange();
      } catch (KeyStoreException e) {
        onException(e);
      } catch (FileNotFoundException e) {
        onException(e);
      } catch (NoSuchAlgorithmException e) {
        onException(e);
      } catch (CertificateException e) {
        onException(e);
      } catch (IOException e) {
        onException(e);
      }
      finally {
        this.mDisableOnChange = false;
      }
    }
  }

  public IWizardPage getPreviousPage()
  {
    return this.mWizard.getKeystoreSelectionPage();
  }

  public IWizardPage getNextPage()
  {
    if (this.mWizard.getKeyCreationMode()) {
      return this.mWizard.getKeyCreationPage();
    }

    return this.mWizard.getKeyCheckPage();
  }

  private void onChange()
  {
    if (this.mDisableOnChange) {
      return;
    }

    setErrorMessage(null);
    setMessage(null);

    if (!this.mWizard.getKeyCreationMode()) {
      if (this.mKeyAliases.getSelectionIndex() == -1) {
        setErrorMessage("Select a key alias.");
        setPageComplete(false);
        return;
      }

      if (this.mKeyPassword.getText().trim().length() == 0) {
        setErrorMessage("Enter key password.");
        setPageComplete(false);
        return;
      }
    }

    setPageComplete(true);
  }

  private void enableWidgets() {
    boolean useKey = !this.mWizard.getKeyCreationMode();
    this.mKeyAliasesLabel.setEnabled(useKey);
    this.mKeyAliases.setEnabled(useKey);
    this.mKeyPassword.setEnabled(useKey);
    this.mKeyPasswordLabel.setEnabled(useKey);
  }
}