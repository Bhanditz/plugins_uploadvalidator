// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.uploadvalidator;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

public class SubmoduleValidator implements CommitValidationListener {

  public static AbstractModule module() {
    return new AbstractModule() {

      @Override
      protected void configure() {
        DynamicSet.bind(binder(), CommitValidationListener.class).to(SubmoduleValidator.class);
        bind(ProjectConfigEntry.class)
            .annotatedWith(Exports.named(KEY_CHECK_SUBMODULE))
            .toInstance(
                new ProjectConfigEntry(
                    "Reject Submodules",
                    "false",
                    ProjectConfigEntryType.BOOLEAN,
                    null,
                    false,
                    "Pushes of " + "commits that include submodules will be rejected."));
      }
    };
  }

  public static final String KEY_CHECK_SUBMODULE = "rejectSubmodule";

  private final String pluginName;
  private final PluginConfigFactory cfgFactory;
  private final GitRepositoryManager repoManager;
  private final ValidatorConfig validatorConfig;

  @Inject
  SubmoduleValidator(
      @PluginName String pluginName,
      PluginConfigFactory cfgFactory,
      GitRepositoryManager repoManager,
      ValidatorConfig validatorConfig) {
    this.pluginName = pluginName;
    this.cfgFactory = cfgFactory;
    this.repoManager = repoManager;
    this.validatorConfig = validatorConfig;
  }

  static boolean isActive(PluginConfig cfg) {
    return cfg.getBoolean(KEY_CHECK_SUBMODULE, false);
  }

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    try {
      PluginConfig cfg =
          cfgFactory.getFromProjectConfigWithInheritance(
              receiveEvent.project.getNameKey(), pluginName);
      if (isActive(cfg)
          && validatorConfig.isEnabledForRef(
              receiveEvent.user,
              receiveEvent.getProjectNameKey(),
              receiveEvent.getRefName(),
              KEY_CHECK_SUBMODULE)) {
        try (Repository repo = repoManager.openRepository(receiveEvent.project.getNameKey())) {
          List<CommitValidationMessage> messages =
              performValidation(repo, receiveEvent.commit, receiveEvent.revWalk);
          if (!messages.isEmpty()) {
            throw new CommitValidationException("contains submodules", messages);
          }
        }
      }
    } catch (NoSuchProjectException | IOException e) {
      throw new CommitValidationException("failed to check on submodules", e);
    }
    return Collections.emptyList();
  }

  private static void addValidationMessage(List<CommitValidationMessage> messages, TreeWalk tw) {
    messages.add(
        new CommitValidationMessage("submodules are not allowed: " + tw.getPathString(), true));
  }

  private static boolean isSubmodule(TreeWalk tw) {
    return (tw.getRawMode(0) & FileMode.TYPE_MASK) == FileMode.TYPE_GITLINK;
  }

  static List<CommitValidationMessage> performValidation(
      Repository repo, RevCommit c, RevWalk revWalk) throws IOException {
    final List<CommitValidationMessage> messages = new LinkedList<>();

    CommitUtils.visitChangedEntries(
        repo,
        c,
        revWalk,
        new TreeWalkVisitor() {
          @Override
          public void onVisit(TreeWalk tw) {
            if (isSubmodule(tw)) {
              addValidationMessage(messages, tw);
            }
          }
        });
    return messages;
  }
}
