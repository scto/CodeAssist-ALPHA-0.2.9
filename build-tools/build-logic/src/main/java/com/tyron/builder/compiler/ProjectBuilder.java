package com.tyron.builder.compiler;

import androidx.annotation.NonNull;

import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.ModuleSettings;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.deenu143.gradle.utils.GradleUtils;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ProjectBuilder {

    private final List<Module> mModules;
    private final Project mProject;
    private final ILogger mLogger;
    private Builder.TaskListener mTaskListener;

    public ProjectBuilder(Project project, ILogger logger) throws IOException {
        mProject = project;
        mLogger = logger;
        mModules = project.getBuildOrder();
    }

    public void setTaskListener(@NonNull Builder.TaskListener listener) {
        mTaskListener = listener;
    }

    public void build(BuildType type) throws IOException, CompilationFailedException {
        for (Module module : mModules) {
            module.clear();
            module.open();
            module.index();

            Builder<? extends Module> builder;
			AndroidModule androidModule = (AndroidModule) module;
            String moduleType = module.getPlugins();
			
            switch (Objects.requireNonNull(moduleType)) {
                case "java-library":
                    builder = new JarBuilder(mProject, (JavaModule) module, mLogger);
                    break;		
                default:
                case "com.android.application":          
                    if (type == BuildType.AAB) {
                        builder = new AndroidAppBundleBuilder(mProject, androidModule, mLogger);
                    } else {
                        builder = new AndroidAppBuilder(mProject, androidModule, mLogger);
                    }
                    break;
            }
            builder.setTaskListener(mTaskListener);
            builder.build(type);
        }
    }

}
