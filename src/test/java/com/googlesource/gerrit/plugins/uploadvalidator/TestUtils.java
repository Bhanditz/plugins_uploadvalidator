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

import com.google.gerrit.server.git.validators.CommitValidationMessage;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestUtils {
  public static Repository createNewRepository(File repoFolder)
      throws IOException {
    Repository repository =
        FileRepositoryBuilder.create(new File(repoFolder, ".git"));
    repository.create();
    return repository;
  }

  public static RevCommit makeCommit(Repository repo, String message,
      Set<File> files)
          throws IOException, NoFilepatternException, GitAPIException {
    Map<File, byte[]> tmp = new HashMap<>();
    for (File f : files) {
      tmp.put(f, null);
    }
    return makeCommit(repo, message, tmp);
  }

  public static RevCommit makeCommit(Repository repo, String message,
      Map<File, byte[]> files)
          throws IOException, NoFilepatternException, GitAPIException {
    try (Git git = new Git(repo)) {
      if (files != null) {
        addFiles(git, files);
      }
      return git.commit().setMessage(message).call();
    }
  }

  private static void addFiles(Git git, Map<File, byte[]> files)
      throws IOException, NoFilepatternException, GitAPIException {
    AddCommand ac = git.add();
    for (File f : files.keySet()) {
      if (!f.exists()) {
        FileUtils.touch(f);
      }
      if (files.get(f) != null) {
        FileUtils.writeByteArrayToFile(f, files.get(f));
      }
      String p = f.getAbsolutePath()
          .replace(git.getRepository().getWorkTree().getAbsolutePath(), "")
          .substring(1);
      ac = ac.addFilepattern(p);
    }
    ac.call();
  }

  public static boolean compareCommitValidationMessage(
      List<CommitValidationMessage> m1, List<CommitValidationMessage> m2) {
    for (CommitValidationMessage cvm1 : m1) {
      boolean found = false;
      for (CommitValidationMessage cvm2 : m2) {
        if (compareCommitValidationMessage(cvm1, cvm2)) {
          found = true;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  public static boolean compareCommitValidationMessage(
      CommitValidationMessage msg1, CommitValidationMessage msg2) {
    if (msg1.getMessage().equals(msg2.getMessage())
        && msg1.isError() == msg2.isError()) {
      return true;
    } else {
      return false;
    }
  }
}