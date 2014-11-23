package com.rocwinger.android.multiapk.export;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.android.ide.common.xml.ManifestData;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectChooserHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;

final class ProjectCheckPage extends ExportWizard.ExportWizardPage
{
  private static final String IMG_ERROR = "error.png";
  private static final String IMG_WARNING = "warning.png";
  private final ExportWizard mWizard;
  private Image mError;
  private Image mWarning;
  private boolean mHasMessage = false;
  private Composite mTopComposite;
  private Composite mErrorComposite;
  private Text mProjectText;
  private ProjectChooserHelper mProjectChooserHelper;
  private boolean mFirstOnShow = true;

  protected ProjectCheckPage(ExportWizard wizard, String pageName) {
    super(pageName);
    this.mWizard = wizard;

    setTitle("Project Checks");
    setDescription("Performs a set of checks to make sure the application can be exported.");
  }

  public void createControl(Composite parent)
  {
    this.mProjectChooserHelper = new ProjectChooserHelper(parent.getShell(), 
      new ProjectChooserHelper.NonLibraryProjectOnlyFilter());

    GridLayout gl = null;
    GridData gd = null;

    this.mTopComposite = new Composite(parent, 0);
    this.mTopComposite.setLayoutData(new GridData(1808));
    this.mTopComposite.setLayout(new GridLayout(1, false));

    Composite projectComposite = new Composite(this.mTopComposite, 0);
    projectComposite.setLayoutData(new GridData(768));
    projectComposite.setLayout(gl = new GridLayout(3, false));
    gl.marginHeight = (gl.marginWidth = 0);

    Label label = new Label(projectComposite, 0);
    label.setLayoutData(gd = new GridData(768));
    gd.horizontalSpan = 3;
    label.setText("Select the project to export:");

    new Label(projectComposite, 0).setText("Project:");
    this.mProjectText = new Text(projectComposite, 2048);
    this.mProjectText.setLayoutData(gd = new GridData(768));
    this.mProjectText.addModifyListener(new ModifyListener()
    {
      public void modifyText(ModifyEvent e) {
        ProjectCheckPage.this.handleProjectNameChange();
      }
    });
    Button browseButton = new Button(projectComposite, 8);
    browseButton.setText("Browse...");
    browseButton.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e) {
        IJavaProject javaProject = ProjectCheckPage.this.mProjectChooserHelper.chooseJavaProject(
          ProjectCheckPage.this.mProjectText.getText().trim(), 
          "Please select a project to export");

        if (javaProject != null) {
          IProject project = javaProject.getProject();

          ProjectCheckPage.this.mProjectText.setText(project.getName());
        }
      }
    });
    setControl(this.mTopComposite);
  }

  void onShow()
  {
    if (this.mFirstOnShow)
    {
      IProject project = this.mWizard.getProject();
      if (project != null) {
        this.mProjectText.setText(project.getName());
      }

      this.mFirstOnShow = false;
    }
  }

  private void buildErrorUi(IProject project)
  {
    setErrorMessage(null);
    setMessage(null);
    setPageComplete(true);
    this.mHasMessage = false;

    GridLayout gl = null;
    this.mErrorComposite = new Composite(this.mTopComposite, 0);
    this.mErrorComposite.setLayoutData(new GridData(768));
    gl = new GridLayout(2, false);
    gl.marginHeight = (gl.marginWidth = 0);
    gl.verticalSpacing *= 3;
    this.mErrorComposite.setLayout(gl);

    if (project == null) {
      setErrorMessage("Select project to export.");
      this.mHasMessage = true;
    } else {
      try {
        if (!project.hasNature("com.android.ide.eclipse.adt.AndroidNature")) {
          addError(this.mErrorComposite, "Project is not an Android project.");
        }
        else {
          if (ProjectHelper.hasError(project, true)) {
            addError(this.mErrorComposite, "Project has compilation error(s)");
          }

          IFolder outputIFolder = BaseProjectHelper.getJavaOutputFolder(project);
          if (outputIFolder == null) {
            addError(this.mErrorComposite, 
              "Unable to get the output folder of the project!");
          }

          ManifestData manifestData = AndroidManifestHelper.parseForData(project);
          Boolean debuggable = null;
          if (manifestData != null) {
            debuggable = manifestData.getDebuggable();
          }

          if ((debuggable != null) && (debuggable == Boolean.TRUE)) {
            addWarning(this.mErrorComposite, 
              "The manifest 'debuggable' attribute is set to true.\nYou should set it to false for applications that you release to the public.\n\nApplications with debuggable=true are compiled in debug mode always.");
          }

        }

      }
      catch (CoreException localCoreException)
      {
        addError(this.mErrorComposite, "Unable to get project nature");
      }
    }

    if (!this.mHasMessage) {
      Label label = new Label(this.mErrorComposite, 0);
      GridData gd = new GridData(768);
      gd.horizontalSpan = 2;
      label.setLayoutData(gd);
      label.setText("No errors found. Click Next.");
    }

    this.mTopComposite.layout();
  }

  private void addError(Composite parent, String message)
  {
    if (this.mError == null) {
      this.mError = IconFactory.getInstance().getIcon("error.png");
    }

    new Label(parent, 0).setImage(this.mError);
    Label label = new Label(parent, 0);
    label.setLayoutData(new GridData(768));
    label.setText(message);

    setErrorMessage("Application cannot be exported due to the error(s) below.");
    setPageComplete(false);
    this.mHasMessage = true;
  }

  private void addWarning(Composite parent, String message)
  {
    if (this.mWarning == null) {
      this.mWarning = IconFactory.getInstance().getIcon("warning.png");
    }

    new Label(parent, 0).setImage(this.mWarning);
    Label label = new Label(parent, 0);
    label.setLayoutData(new GridData(768));
    label.setText(message);

    this.mHasMessage = true;
  }

  private void handleProjectNameChange()
  {
    setPageComplete(false);

    if (this.mErrorComposite != null) {
      this.mErrorComposite.dispose();
      this.mErrorComposite = null;
    }

    this.mWizard.setProject(null);

    String text = this.mProjectText.getText().trim();
    if (text.length() == 0) {
      setErrorMessage("Select project to export.");
    } else if (!text.matches("[a-zA-Z0-9_ \\.-]+")) {
      setErrorMessage("Project name contains unsupported characters!");
    } else {
      IJavaProject[] projects = this.mProjectChooserHelper.getAndroidProjects(null);
      IProject found = null;
      for (IJavaProject javaProject : projects) {
        if (javaProject.getProject().getName().equals(text)) {
          found = javaProject.getProject();
          break;
        }

      }

      if (found != null) {
        setErrorMessage(null);

        this.mWizard.setProject(found);

        buildErrorUi(found);
      } else {
        setErrorMessage(String.format("There is no android project named '%1$s'", new Object[] { 
          text }));
      }
    }
  }
}