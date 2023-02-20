package com.tyron.code.ui.main.action.compile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.LinearLayout;

import com.tyron.actions.ActionGroup;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.code.R;
import com.tyron.code.ui.main.CompileCallback;
import com.tyron.code.ui.main.MainFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.tyron.builder.project.Project;
import com.tyron.builder.compiler.BuildType;

import java.util.ArrayList;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.deenu143.gradle.utils.GradleUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.util.List;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.ui.main.IndexCallback;
import androidx.annotation.CallSuper;

import com.tyron.fileeditor.api.FileEditor;
import com.tyron.code.ui.editor.Savable;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.io.IOException;
import androidx.annotation.WorkerThread;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Optional;
import java.time.Instant;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.fileeditor.api.FileEditor;
import org.apache.commons.io.FileUtils;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.FileManager;
import com.tyron.builder.project.api.Module;
import java.nio.charset.StandardCharsets;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class CompileActionGroup extends ActionGroup {

    public static final String ID = "compileActionGroup";

    @Override
    public void update(@NonNull AnActionEvent event) {
        CompileCallback data = event.getData(MainFragment.COMPILE_CALLBACK_KEY);
        if (data == null) {
            event.getPresentation().setVisible(false);
            return;
        }

        if (!ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace())) {
            event.getPresentation().setVisible(false);
            return;
        }

        Context context = event.getData(CommonDataKeys.CONTEXT);
        if (context == null) {
            event.getPresentation().setVisible(false);
            return;
        }

        event.getPresentation().setVisible(true);
        event.getPresentation().setEnabled(false);
        event.getPresentation().setText(context.getString(R.string.menu_run));
        event.getPresentation().setIcon(ContextCompat.getDrawable(context, R.drawable.round_play_arrow_24));

		MainViewModel mainViewModel = event.getData(MainFragment.MAIN_VIEW_MODEL_KEY);
        if (mainViewModel == null) {		
			return; 
		}
		Boolean indexing = mainViewModel.isIndexing().getValue();
        if (indexing == null) {
            indexing = false;
        }

		Project project = event.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            return;
        }

		if (project != null && !project.isCompiling() && !indexing) {      
			event.getPresentation().setEnabled(true);	
		}
	}

	@CallSuper
    @Override
    public void actionPerformed(@NonNull AnActionEvent event) {
		Context context = event.getData(CommonDataKeys.CONTEXT);
		CompileCallback callback = event.getData(MainFragment.COMPILE_CALLBACK_KEY);
		MainViewModel viewModel = event.getRequiredData(MainFragment.MAIN_VIEW_MODEL_KEY);
		Project project = event.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            return;
        }
		
		List<FileEditor> editors = viewModel.getFiles()
			.getValue();
        if (editors == null) {
            return;
        } 
		//save files before build
		Stream<FileEditor> validEditors = editors.stream()
			.filter(it -> it.getFragment() instanceof Savable)
		.filter(it -> ((Savable) it.getFragment()).canSave());
        List<File> filesToSave = validEditors
			.map(FileEditor::getFile)
		.collect(Collectors.toList());
     
        ProgressManager.getInstance()
			.runNonCancelableAsync(() -> {
			List<IOException> exceptions = saveFiles(project, filesToSave);
			if (!exceptions.isEmpty()) {
				new MaterialAlertDialogBuilder(event.getDataContext()).setTitle(R.string.error)
					.setPositiveButton(android.R.string.ok, null)
					.setMessage(exceptions.stream()
								.map(IOException::getMessage)
				.collect(Collectors.joining("\n\n")))
				.show();
			}
		});

	    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
		bottomSheetDialog.setContentView(R.layout.layout_dialog_project_status);
		ConstraintLayout projectStatus = bottomSheetDialog.findViewById(R.id.project_status);

		File gradleFile = project.getMainModule().getGradleFile();
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		int lastSelectedIndex = preferences.getInt("last_selected_index", 1);
		
		try {
			List<String> plugins = GradleUtils.parsePlugins(gradleFile);
			if (!plugins.contains("com.android.application")) {
				// Show the bottom sheet dialog only if the list of plugins doesn't contain "com.android.application"
				projectStatus.setVisibility(View.VISIBLE);
				bottomSheetDialog.show();
			} else {
				switch (lastSelectedIndex) {
					case 0:
						callback.compile(BuildType.RELEASE);
						break;
					case 1:
						callback.compile(BuildType.DEBUG);
						break;
					case 2:
						callback.compile(BuildType.AAB);
						break;
				}
			}
		} catch (Exception e) {
			projectStatus.setVisibility(View.VISIBLE);
			bottomSheetDialog.show();
		}	
    }	

    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
		return new AnAction[]{};
    }
	
	@WorkerThread
    private static List<IOException> saveFiles(Project project, List<File> files) {
        List<IOException> exceptions = new ArrayList<>();
        for (File file : files) {
            Module module = project.getModule(file);
            if (module == null) {
                // TODO: try to save files without a module
                continue;
            }

            FileManager fileManager = module.getFileManager();
            Optional<CharSequence> fileContent = fileManager.getFileContent(file);
            if (fileContent.isPresent()) {
                try {
                    FileUtils.writeStringToFile(file, fileContent.get()
												.toString(), StandardCharsets.UTF_8);
                    Instant instant = Instant.ofEpochMilli(file.lastModified());

                    ProgressManager.getInstance()
						.runLater(() -> fileManager.setLastModified(file, instant));
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
        }
        return exceptions;
    }
}
