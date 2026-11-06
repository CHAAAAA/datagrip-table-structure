# Editor-tab mechanics for a DataGrip 2026.2 plugin (table structure in a normal editor tab)

**Scope.** How to show an Oracle table's structure (columns / indexes / constraints) in a **normal editor tab**
(not a dialog), on IntelliJ Platform / DataGrip **2026.2.6, build DB-262.10968.148**.

**Method.** No internet. Everything below was read out of the installed jars with
`/usr/bin/javap` (signatures, `-c` bytecode, `-v` annotations) and by extracting every
`META-INF/*.xml` descriptor from every jar in the installation (494 files) and grepping the
extension-point declarations and registrations. Skeleton code was **actually compiled** with the
bundled JBR 25 `javac` against these jars (see §8). Claims are tagged:

* **[V]** verified with `javap` (signature or bytecode)
* **[X]** verified from extracted `plugin.xml` / descriptors
* **[I]** inferred / standard practice, not fully verified

Environment facts used: platform jars in `~/Applications/DataGrip.app/Contents/lib/*.jar`,
DataGrip plugin in `Contents/plugins/DatabaseTools/lib/` (`database-plugin.jar` holds the
descriptor, `modules/intellij.database.*.jar` hold the classes). Class-file major version is **69
(Java 25)**, so plain `/usr/bin/javac` (21) cannot compile against them — use
`~/Applications/DataGrip.app/Contents/jbr/Contents/Home/bin/javac` (25.0.4).

---

## 1. Verified API signatures (platform, 2026.2)

### 1.1 `com.intellij.openapi.fileEditor.FileEditorProvider` — `intellij.platform.analysis.jar`

```java
public interface FileEditorProvider extends com.intellij.openapi.project.PossiblyDumbAware {
  ExtensionPointName<FileEditorProvider> EP_FILE_EDITOR_PROVIDER;
  Key<FileEditorProvider> KEY;
  FileEditorProvider[] EMPTY_ARRAY;

  boolean accept(Project project, VirtualFile file);              // abstract
  default boolean acceptRequiresReadAction();                     // default: true
  FileEditor createEditor(Project project, VirtualFile file);     // abstract  <-- NOT createFileEditor
  default void disposeEditor(FileEditor editor);
  default FileEditorState readState(Element element, Project project, VirtualFile file);
  default void writeState(FileEditorState state, Project project, Element element);
  String getEditorTypeId();                                       // abstract
  FileEditorPolicy getPolicy();                                   // abstract
}
```

**[V] There is no `createFileEditor` on `FileEditorProvider`.** The task brief assumed
`createFileEditor`; the real name is **`createEditor(Project, VirtualFile)`**. The name
`createFileEditor` exists only on the *async* provider:

```java
// com.intellij.openapi.fileEditor.AsyncFileEditorProvider — intellij.platform.ide.jar
Object createFileEditor(Project, VirtualFile, Document, CoroutineScope, Continuation<? super TextEditor>); // suspend, Kotlin
```

`TextEditorProvider` implements it (`intellij.platform.ide.impl.jar`):

```java
public class TextEditorProvider implements DefaultPlatformFileEditorProvider, TextBasedFileEditorProvider,
        StructureViewFileEditorProvider, QuickDefinitionProvider, AsyncFileEditorProvider {
  public boolean accept(Project, VirtualFile);                       // accepts only non-binary text files
  public FileEditor createEditor(Project, VirtualFile);
  public FileEditorState readState(Element, Project, VirtualFile);
  public void writeState(FileEditorState, Project, Element);
  public String getEditorTypeId();                                   // "TextEditor"
  public FileEditorPolicy getPolicy();                               // NONE
  public static TextEditorProvider getInstance();
  public static boolean isTextFile(VirtualFile);
}
```

`TextEditorProvider` is **not** a base class you can reuse to host custom Swing content — it is the
platform's text editor provider, wired to `EditorFactory`/`Document`. Do not extend it.

`PossiblyDumbAware` **[V]**:

```java
public interface PossiblyDumbAware { default boolean isDumbAware(); }  // body: return this instanceof DumbAware
public interface DumbAware { }                                          // marker
```

### 1.2 `com.intellij.openapi.fileEditor.FileEditor` — `intellij.platform.editor.ui.jar`

```java
public interface FileEditor extends UserDataHolder, Disposable {
  Key<VirtualFile> FILE_KEY;
  static String getPropModified();
  static String getPropValid();

  JComponent getComponent();                       // abstract
  JComponent getPreferredFocusedComponent();       // abstract
  String getName();                                // abstract
  default FileEditorState getState(FileEditorStateLevel level);   // returns FileEditorState.INSTANCE
  void setState(FileEditorState state);            // abstract
  default void setState(FileEditorState state, boolean exactState);
  boolean isModified();                            // abstract
  boolean isValid();                               // abstract
  default void selectNotify();
  default void deselectNotify();
  void addPropertyChangeListener(PropertyChangeListener listener);  // abstract
  void removePropertyChangeListener(PropertyChangeListener listener);// abstract
  default BackgroundEditorHighlighter getBackgroundHighlighter();
  default FileEditorLocation getCurrentLocation();
  default StructureViewBuilder getStructureViewBuilder();
  default VirtualFile getFile();                   // !! must be overridden, see below
  default List<VirtualFile> getFilesToRefresh();
  default ActionGroup getTabActions();
}
```

**[V] `getFile()`'s default body calls
`PluginException.reportDeprecatedDefault(..., "A proper @NotNull implementation required")`** and
then returns `FILE_KEY.get(this)`. So **override `getFile()`** — otherwise you get a logged plugin
error and a `null` file.

**[V]** `getState(level)`'s default returns `FileEditorState.INSTANCE`, so only `setState` is
mandatory. `FileEditorState` is a one-method interface:

```java
public interface FileEditorState {
  FileEditorState INSTANCE;
  boolean canBeMergedWith(FileEditorState other, FileEditorStateLevel level);
}
public enum FileEditorStateLevel { FULL, UNDO, NAVIGATION }
```

### 1.3 `FileEditorPolicy` — `intellij.platform.analysis.jar`

```java
public enum FileEditorPolicy { PLACE_BEFORE_DEFAULT_EDITOR, NONE, HIDE_DEFAULT_EDITOR, HIDE_OTHER_EDITORS,
                               PLACE_AFTER_DEFAULT_EDITOR }
```

**[V] Guard rail:** `FileEditorProviderManagerImplKt.checkPolicy()` logs a `PluginException` when
`getPolicy() == HIDE_DEFAULT_EDITOR` **and** the provider is not dumb-aware
(`DumbService.isDumbAware(provider)`). If you return `HIDE_DEFAULT_EDITOR`, also implement
`DumbAware`.

### 1.4 `FileEditorManager` / `FileEditorManagerEx`

`com.intellij.openapi.fileEditor.FileEditorManager` (abstract class, `intellij.platform.analysis.jar`) **[V]**:

```java
static FileEditorManager getInstance(Project);
FileEditor[]  openFile(VirtualFile file, boolean focus);              // abstract
List<FileEditor> openFile(VirtualFile file);                          // abstract
FileEditor[]  openFile(VirtualFile file, boolean focus, boolean pinned);// concrete helper
void          requestOpenFile(VirtualFile file);                      // concrete
boolean canOpenFile(VirtualFile file);                                // abstract
void closeFile(VirtualFile file);                                     // abstract
boolean isFileOpen(VirtualFile file);
VirtualFile[] getOpenFiles();
VirtualFile getCurrentFile();
FileEditor getSelectedEditor(VirtualFile file);
FileEditor[] getEditors(VirtualFile file);
void setSelectedEditor(VirtualFile file, String editorTypeId);        // abstract
void addFileEditorManagerListener(FileEditorManagerListener listener);
void updateFilePresentation(VirtualFile file);                        // repaint tab text/icon
boolean hasPinnedEditorTab(VirtualFile); void setPinnedEditorTab(VirtualFile, boolean);
StateFlow<FileEditor> getSelectedEditorFlow();                        // new coroutine API
```

**[V] Package move:** `FileEditorManagerEx` now lives in **`com.intellij.openapi.fileEditor.ex`**
(`intellij.platform.ide.impl.jar`); the historical `com.intellij.openapi.fileEditor.impl.FileEditorManagerEx`
**does not exist anywhere in this build** (checked all jars). Signature highlights:

```java
public abstract class FileEditorManagerEx extends FileEditorManager {
  static FileEditorManagerEx getInstanceEx(Project);
  Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(VirtualFile);
  FileEditorComposite openFile(VirtualFile, FileEditorOpenRequest);          // new
  Object openFile(VirtualFile, FileEditorOpenOptions, Continuation<FileEditorComposite>); // suspend
  EditorWindow getCurrentWindow(); EditorWindow[] getWindows();
  void updateFileName(VirtualFile);
}
```

For our use case `FileEditorManager.getInstance(project).openFile(vf, true)` is enough; nothing from
`FileEditorManagerEx` is required.

### 1.5 `com.intellij.openapi.fileTypes.FileType` — `intellij.platform.core.jar`

```java
public interface FileType {
  FileType[] EMPTY_ARRAY;
  String getName();                              // abstract
  default String getDisplayName();               // default: getName()
  String getDescription();                       // abstract
  String getDefaultExtension();                  // abstract
  Icon getIcon();                                // abstract
  boolean isBinary();                            // abstract
  default boolean isReadOnly();                  // default false
  default String getCharset(VirtualFile, byte[]);
  default boolean isCharsetHardcoded();
}
```

So exactly **five** abstract methods: `getName`, `getDescription`, `getDefaultExtension`, `getIcon`,
`isBinary`.

`com.intellij.openapi.fileTypes.FileTypeFactory` still exists (`intellij.platform.ide.core.jar`) but is
**`@Deprecated` [V]** — `<fileTypeFactory>` is legacy. Register file types directly with `<fileType>`.

### 1.6 `LightVirtualFile` and its hidden file system

```java
// com.intellij.testFramework.LightVirtualFile — intellij.platform.core.jar  [V] (no @Deprecated, no @Internal)
public class LightVirtualFile extends LightVirtualFileBase {
  LightVirtualFile();
  LightVirtualFile(String name);
  LightVirtualFile(String name, CharSequence text);
  LightVirtualFile(String name, FileType fileType, CharSequence text);
  LightVirtualFile(VirtualFile original, CharSequence text, long modificationStamp);
  LightVirtualFile(String name, FileType fileType, CharSequence text, long modificationStamp);
  LightVirtualFile(String name, FileType fileType, CharSequence text, Charset charset, long modificationStamp);
  LightVirtualFile(String name, Language language, CharSequence text);
  static Builder builder();
  void setFileType(FileType);            // inherited from LightVirtualFileBase
  void setContent(Object requestor, CharSequence content, boolean fireEvent);
  CharSequence getContent();
  boolean shouldSkipEventSystem();
}

// com.intellij.testFramework.LightVirtualFileBase  [V]
public abstract class LightVirtualFileBase extends VirtualFile
        implements VirtualFileWithAssignedFileType {
  LightVirtualFileBase(String name, FileType fileType, long modificationStamp);
  public void setFileType(FileType);
  public FileType getAssignedFileType();
  public VirtualFileSystem getFileSystem();     // returns the private inner MyVirtualFileSystem
  public String getPath();                      // parent==null -> just the name
  public boolean isWritable(); public boolean isDirectory(); public boolean isValid(); public void setValid(boolean);
  ...
}
```

**[V] Critical detail — the invisible VFS.** `LightVirtualFileBase.getFileSystem()` returns
`LightVirtualFileBase$MyVirtualFileSystem`, a **package-private inner class**, whose
`getProtocol()` returns the literal string **`"mock"`**, and which is **not registered** through the
`com.intellij.virtualFileSystem` extension point (see §1.9: the complete list of registered VFS
protocols contains no `mock`). Consequences:

* `VirtualFile.getUrl()` for a light file is `mock://<name>`.
* `VirtualFileManager.getInstance().getFileSystem("mock")` → `null` (+ logged error).
* `VirtualFileManager.findFileByUrl("mock://…")` → `null`, forever, including after a restart.
  This is the mechanical reason light-file tabs cannot be restored (§3).

`LightVirtualFileBase` implements `VirtualFileWithAssignedFileType`, so the explicitly assigned
`FileType` wins over name/extension detection — the file name is free-form (`"ORDERS (structure)"`
works, and the tab title is exactly that name, see §2.1).

### 1.7 `VirtualFile` — the abstract surface you must implement for a custom VFS

`intellij.platform.core.jar`, `public abstract class VirtualFile extends UserDataHolderBase implements ModificationTracker` **[V]**.
Abstract members (the minimum for option B):

```java
String getName();
VirtualFileSystem getFileSystem();
boolean isWritable();
boolean isDirectory();
boolean isValid();
VirtualFile getParent();
VirtualFile[] getChildren();
OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException;
byte[] contentsToByteArray() throws IOException;
long getTimeStamp();
long getLength();
InputStream getInputStream() throws IOException;
void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable);
```

Concrete and overridable: `getPath()`, `getUrl()`, `getExtension()`, `getFileType()`,
`getPresentableName()`, `getModificationStamp()`, `getCharset()`, `toNioPath()`, `isInLocalFileSystem()`.

### 1.8 `VirtualFileSystem` — `intellij.platform.core.jar`

```java
public abstract class VirtualFileSystem {
  ExtensionPointName<KeyedLazyInstance<VirtualFileSystem>> EP_NAME;   // "com.intellij.virtualFileSystem"
  abstract String getProtocol();
  abstract VirtualFile findFileByPath(String path);
  abstract void refresh(boolean asynchronous);
  abstract VirtualFile refreshAndFindFileByPath(String path);
  abstract void addVirtualFileListener(VirtualFileListener); / removeVirtualFileListener(...)
  protected abstract void deleteFile/moveFile/renameFile/createChildFile/createChildDirectory/copyFile(...);
  abstract boolean isReadOnly();
  boolean isCaseSensitive();
}
```

### 1.9 Extension points (declarations + registrations) — **[X]**

Extracted from `META-INF/PlatformExtensionPoints.xml` (`intellij.platform.ide.impl.jar`),
`META-INF/CoreImpl.analyzer.xml` (`intellij.platform.core.impl.jar`), `META-INF/Core.xml`
(`intellij.platform.core.jar`) and every plugin descriptor in the installation.

```xml
<!-- intellij.platform.ide.impl.jar : META-INF/PlatformExtensionPoints.xml -->
<extensionPoint name="fileType"
                beanClass="com.intellij.openapi.fileTypes.impl.FileTypeBean"
                dynamic="true">
  <with attribute="implementationClass" implements="com.intellij.openapi.fileTypes.FileType"/>
</extensionPoint>

<extensionPoint name="fileEditorProvider"
                interface="com.intellij.openapi.fileEditor.FileEditorProvider"
                hasAttributes="true"          <!-- = arbitrary custom attributes allowed -->
                dynamic="true"/>

<extensionPoint name="fileEditorProviderSuppressor"
                interface="com.intellij.openapi.fileEditor.impl.FileEditorProviderSuppressor" dynamic="true"/>

<extensionPoint name="fileTypeFactory" interface="com.intellij.openapi.fileTypes.FileTypeFactory" dynamic="false"/>
<extensionPoint name="editorTabTitleProvider"
                interface="com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider" dynamic="true"/>
```

```xml
<!-- intellij.platform.core.impl.jar : META-INF/CoreImpl.analyzer.xml  (VFS registration point) -->
<extensionPoint qualifiedName="com.intellij.virtualFileSystem"
                beanClass="com.intellij.openapi.vfs.impl.VirtualFileManagerImpl$VirtualFileSystemBean"
                dynamic="true">
  <with attribute="implementationClass" implements="com.intellij.openapi.vfs.VirtualFileSystem"/>
</extensionPoint>
```

Bean fields **[V]**:

```java
final class VirtualFileManagerImpl$VirtualFileSystemBean extends KeyedLazyInstanceEP<VirtualFileSystem> {
  public boolean physical;
}                                        // attributes: key, implementationClass, physical

public final class FileTypeBean {        // attributes for <fileType>
  public String implementationClass, fieldName, name, extensions, fileNames, patterns,
                fileNamesCaseInsensitive, language, hashBangs;
}
```

**`<fileEditorProvider>` attributes actually read by the platform [V]** (bytecode of
`FileEditorProviderManagerImplKt` / `FileEditorProviderManagerImpl`):

| attribute | read by | meaning |
|---|---|---|
| `id` | `ExtensionPointName.findByIdOrFromInstance` (`getProvider(String editorTypeId)`) | EP id; becomes the addressable editor type id used for `selectedProvider` persistence |
| `fileType` | `LazyExtension.getCustomAttribute("fileType")` in `getFileType()` / `isAcceptedByFileType()` | pre-filter: `accept()` is only called for files of that FileType (matched by name) |
| `isDocumentRequired` | `LazyExtension.getCustomAttribute("isDocumentRequired")` → `Boolean.parseBoolean` | provider needs a `Document` (DataGrip uses it: `id="csv-data-editor" … isDocumentRequired="true"`) |

**There is no `order` attribute. [V]** Ordering of applicable providers is done by
`com.intellij.openapi.fileEditor.impl.MyComparator`, whose `compare()` calls
`FileEditorPolicy.compareTo(...)` and then `WeighedFileEditorProvider.getWeight()`:

```java
public abstract class WeighedFileEditorProvider implements FileEditorProvider {  // intellij.platform.ide.jar
  double DEFAULT_WEIGHT;                 // = 1.0  (dconst_1 in <init>)  [V]
  public double getWeight();
  public boolean isDumbAware();
}
```

Registered VFS protocols in the whole installation **[X]** (`<virtualFileSystem>` occurrences):
`file`, `jar`, `http`, `https`, `temp`, `dummy`, `vcs`, `vcs-log`, `settings`, `gitIndexFs`,
`git-compare-branches`, `lazyAttachVfs`, `uml`, `dbSrc`, **`das`** (DataGrip database elements) —
**no `mock`, no `light`**. This is the precise reason light files cannot come back after a restart.

### 1.10 Things that look helpful but are not usable

| API | verdict |
|---|---|
| `com.intellij.openapi.fileEditor.impl.JComponentEditorProvider` (`id="jcomponent-editor"`, platform-registered) | **[V] not usable from a plugin.** It accepts a file only if `file.getUserData(JComponentEditorProviderUtils.JCOMPONENT_KEY) != null`; the accessor is Kotlin-`internal` (`getJCOMPONENT_KEY$intellij_platform_ide_impl`). No public "put a JComponent into a tab" API. |
| `EditorWithProviderComposite` | **[V]** exists (`intellij.platform.ide.impl.jar`) but is `impl`-internal plumbing, not a composition API. |
| `TextEditorProvider` as host for custom content | **[V]** tied to `EditorFactory`/`Document`; cannot host a `JPanel`. |
| `FileTypeFactory` + `<fileTypeFactory>` | **[V]** `FileTypeFactory` is `@Deprecated`; use `<fileType>`. |

---

## 2. Option A — `LightVirtualFile` + custom `FileType` + `FileEditorProvider`

### 2.1 How the tab title / icon are derived **[V]**

Bytecode of `com.intellij.openapi.fileEditor.impl.EditorComposite` references **only
`VirtualFile.getName()`** for the tab text (plus `EditorTabTitleProvider` when a plugin overrides it);
`EditorTabbedContainer` installs the icon through `TabInfo.setIcon(...)` / `TabInfo.setIconHolder(...)`
fed from the file/`FileType`. Therefore:

* **Title** = `VirtualFile.getName()` (or `getPresentableName()`), e.g.
  `new LightVirtualFile("ORDERS (structure)", myFileType, "")` → tab text `ORDERS (structure)`.
  Override with the `editorTabTitleProvider` EP if you need connection/schema decoration.
* **Icon** = the file's `FileType.getIcon()`. `com.intellij.icons.AllIcons` lives in
  `intellij.platform.util.ui.jar`; `AllIcons$Nodes` has `DataTables`, `DataColumn`, `DataSchema`
  **[V]** (use `AllIcons.Nodes.DataTables`). A `VirtualFile` implementing `Iconable` can override the
  icon per file **[I]**.
* `FileEditor.getName()` is **not** the tab title; it is the label of the editor *kind* (used in the
  "editor type" switcher and `EditorComposite$…` labels). Return `"Structure"`.

### 2.2 How to open it **[V]**

```java
VirtualFile vf = new TableStructureVirtualFile(key);       // stable instance, see §7
FileEditorManager.getInstance(project).openFile(vf, true); // focus=true; returns FileEditor[]
```

`openFile` is keyed by the `VirtualFile` **instance** (identity, not name): calling it again with the
same instance re-selects the existing tab, calling it with a *new* equal-looking instance opens a
**second** tab. Keep one instance per table (project service, §8).

### 2.3 What breaks after IDE restart — confirmed

Restoration algorithm, reconstructed from bytecode of
`com.intellij.openapi.fileEditor.impl.EditorsSplittersKt` (`setupFileTab` /
`…resolveFileOrLogError(FileEntry, VirtualFileManager)`) **[V]**:

```java
// FileEntry (com.intellij.platform.fileEditor.FileEntry) persisted fields:
//   url, id (Integer), protocol, managingFsCreationTimestamp, selectedProvider,
//   providers: Map<String, Element> (per-provider state), pinned, isPreview, tab(FileEntryTab), …
file = FileIdAdapter.getFileWithTimestamp(id, entry, ts)   // id path  (only VirtualFileWithId)
    ?: FileIdAdapter.getFile(id, entry)
    ?: FileIdAdapter.getFile(protocol, url, entry)
    ?: virtualFileManager.findFileByUrl(entry.url)          // <-- URL path
    ?: virtualFileManager.refreshAndFindFileByUrl(entry.url)
    ?: { LOG.error(...); /* tab dropped */ }
```

and `com.intellij.openapi.vfs.impl.FileIdAdapterImpl` **[V]**:

```java
Integer getId(VirtualFile f)                 // ((VirtualFileWithId) f).getId(), else null
VirtualFile getFile(int id, FileEntry e)     // VirtualFileManager.findFileById(id)
long getManagingFsCreationTimestamp(VirtualFile) // ManagingFS.getInstance().getCreationTimestamp()
boolean shouldSaveEditorState(VirtualFile)   // always true
```

So **the tab state *is* written** for a light file (`shouldSaveEditorState` = `true`), but on load:

* `getId` → `null` (a `LightVirtualFile` is not a `VirtualFileWithId`);
* `getFile(protocol="mock", url="mock://ORDERS (structure)", …)` → `null`;
* `findFileByUrl("mock://…")` → `null` (no VFS registered for `mock`);
* `refreshAndFindFileByUrl` → `null`;
* ⇒ **error logged, tab silently dropped.**

**Confirmed: a `LightVirtualFile` tab does not survive an IDE restart.** Same for *Reopen Closed Tab*
across restarts (`EditorHistoryManager` also persists URLs), and the file will show up in Recent Files
only for the current session. Practical mitigation: remember the last opened table keys in a
`@State` project service / `PersistentStateComponent` and re-open lazily (e.g. on first project
activity **only if** the data source is connected) — do *not* auto-open tabs eagerly at startup.

### 2.4 Refresh semantics **[V]**

* Content lives in the editor, not in the file: implement `selectNotify()` (called when the tab is
  selected) plus a manual "Refresh" toolbar button. That is what DataGrip does for its structure tab.
* If you *do* want the file to look changed: `LightVirtualFile.setContent(requestor, text, fireEvent=true)`
  fires VFS events; `LightVirtualFileBase.setModificationStamp(long)` exists but is `protected`.
* To repaint the tab itself (title/icon changed): `FileEditorManager.updateFilePresentation(file)`
  (or `FileEditorManagerEx.updateFileName(file)`).
* `FileEditor.getFilesToRefresh()` (default) can point at your file if the editor wants a refresh hook.

### 2.5 plugin.xml `order` / `policy` attributes

* `policy` is **not** a plugin.xml attribute — it is returned by `getPolicy()` **[V]**.
* `order` does not exist for `<fileEditorProvider>` **[V]**; use `WeighedFileEditorProvider.getWeight()`
  (default weight 1.0) or `FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR` if you only need to sit next
  to the text editor.
* Attributes that *do* exist: `id`, `fileType`, `isDocumentRequired` (§1.9).

---

## 3. Option B — custom `VirtualFileSystem` registered via `com.intellij.virtualFileSystem`

**Feasibility: yes, supported, and it is what the platform itself does** for restorable editor tabs:

* `settings` protocol → `SettingsFileSystem` + `SettingsFileEditorProvider` (Settings dialog as virtual files)
* `vcs-log` protocol → `VcsLogVirtualFileSystem` + `VcsLogEditorProvider` (VCS Log as an editor tab)
* `das` protocol → **DataGrip's own** `DatabaseVirtualFileSystem` + the three table-editor providers (§5)
* `uml` protocol → `UmlVirtualFileSystem` + `UmlEditorProvider`

**[V] The extension point exists and is public/dynamic:**
`com.intellij.virtualFileSystem`, bean `VirtualFileManagerImpl$VirtualFileSystemBean`
(`key`, `implementationClass`, `physical`), declared in `intellij.platform.core.impl.jar`
(`META-INF/CoreImpl.analyzer.xml`). Registration form **[X]**:

```xml
<extensions defaultExtensionNs="com.intellij">
  <virtualFileSystem key="dgstructure" implementationClass="com.example.TableStructureVirtualFileSystem"/>
</extensions>
```

**Why it fixes restoration:** the persisted `url` is `dgstructure://<path>`; on load
`findFileByUrl` → your `VirtualFileSystem.findFileByPath(path)` (or
`refreshAndFindFileByPath`), and you can re-create the `VirtualFile` from the path alone because the
path *encodes the table key*. No `FileIdAdapter` needed: there is exactly one implementation
(`FileIdAdapterImpl`, a platform service — a plugin cannot register its own), and its `getId` returns
`null` for non-`VirtualFileWithId` files, which is fine because the URL fallback is next in the chain.

**Best base class — `DummyCachingFileSystem` [V]** (this is what DataGrip subclasses):

```java
// com.intellij.openapi.vfs.ex.dummy — intellij.platform.analysis.impl.jar
public abstract class DummyCachingFileSystem<T extends VirtualFile> extends DummyFileSystem {
  public DummyCachingFileSystem(String protocol);
  public final String getProtocol();
  public final VirtualFile createRoot(String name);
  public final T findFileByPath(String path);              // caches; delegates to findFileByPathInner
  protected abstract T findFileByPathInner(String path);   // <-- your only real work
  protected void clearCache(); protected void cleanup();
  protected void fileRenamed/doRenameFile/beforeFileRename(...);
  public Collection<T> getCachedFiles();
}
public class DummyFileSystem extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem { … }
```

`DeprecatedVirtualFileSystem` and `DummyFileSystem` carry **no** `@Deprecated`/`@ApiStatus.Internal`
annotations in this build **[V]** (the name refers to unsupported write operations). Note that
`DummyFileImpl` / `DummyDirectoryImpl` are package-private, so you must write your own `VirtualFile`
subclass (~13 methods, see the compiled snippet in §8.2) — you cannot reuse the platform's file impl.

**Complexity, honestly:** about **+150–200 LOC** over option A (VFS class ≈ 50 LOC, `VirtualFile`
subclass ≈ 110 LOC, one extra `<virtualFileSystem>` line), plus these responsibilities:

1. **Path ⇄ key codec** — the path *is* the persisted state; keep it stable and URL-safe
   (escape `/`, spaces, quotes; DataGrip has `DatabaseElementVirtualFileUtils.escape/unescape` for
   exactly this).
2. **Lifetime/leaks** — the FS instance lives for the whole application; files must not pin a
   `Project`. Store only ids/paths, resolve the project from the file path or from
   `DummyCachingFileSystem.getProject(String)` (present in the API **[V]**).
3. **Validity** — when a table disappears, `VirtualFile.isValid()` must return `false`;
   the platform then closes/ignores the tab. You need `VirtualFileManager`/VFS events to notify.
4. **Empty content** — `contentsToByteArray()`/`getInputStream()` must not throw; return empty
   (otherwise "Cannot load text" style errors appear for things like copy/path actions).
5. **No index, no `ManagingFS`** — `NonPhysicalFileSystem` means no indexing, no `VirtualFileWithId`,
   no local-file services; also `LocalFileSystem`-only actions will be disabled for these files.
6. **`physical="false"`** — omit `physical` (default `false`) or set it explicitly; `true` would make
   the platform treat it as a real FS (bad).

**Verdict:** worth it only if "tab comes back after restart" is a hard requirement. For a DB table
that may be unreachable at startup, auto-restoring the tab is of questionable value — see §4/§9.

---

## 4. Option comparison

| | **A. `LightVirtualFile` + `<fileType>` + `<fileEditorProvider>`** | **B. Custom VFS (`com.intellij.virtualFileSystem`) + `<fileEditorProvider>`** | **C. Real placeholder file on disk + `<fileType>` + provider** | **D. Reuse `TextEditorProvider` / `EditorWithProviderComposite` / `JComponentEditorProvider`** |
|---|---|---|---|---|
| Effort | **~60 LOC** + plugin.xml (compiles today, §8.1) | **~250 LOC** (adds VFS + `VirtualFile`, §8.2) | ~80 LOC + file lifecycle | n/a |
| Tab survives IDE restart | **No** — verified: `mock://` URL unresolvable, error logged, tab dropped | **Yes** — URL resolves via `findFileByPath` | **Yes** (local FS) | n/a |
| Survives "Reopen Closed Tab" across restarts | No | Yes | Yes | n/a |
| Needs dedupe of VirtualFile instances | Yes (project service) | No (`findFileByPath` caches) | No | n/a |
| Title / icon control | name + `FileType.getIcon()` (+ `editorTabTitleProvider`) | same | same, but name must be a legal file name | n/a |
| Side effects | none on project/VCS/index | none; but a global FS object + validity/lifetime duties | **pollutes project dir / VCS / indexing**; needs cleanup; files can be opened by user | — |
| Risk of breaking between builds | low (`LightVirtualFile` is in production `platform.core.jar`, but in the `testFramework` package) | low (EP + `DummyCachingFileSystem` are stable, used by platform modules) | low | **blocked**: keys are Kotlin-`internal` (§1.10) |
| DataGrip precedent | no (DG uses its own VFS) | **yes** — `das` protocol, `DatabaseVirtualFileSystem extends DummyCachingFileSystem` | no | no |

**Recommendation: Option A now, with the code structured so B is a drop-in upgrade.**

Reasons: (1) the only thing B adds is restart persistence, which for DB-bound content is often
undesirable (connection may be closed/renamed); (2) A is 4 small classes and compiles against 2026.2
with zero deprecation warnings; (3) the migration A→B touches exactly one class — replace
`TableStructureVirtualFile extends LightVirtualFile` by `extends VirtualFile` backed by your own
registered VFS, keep the provider/editor/service unchanged; (4) **do not** use C (project pollution) or
D (not accessible from plugins).

If restart persistence *is* required later: implement option B and additionally re-validate the table
when the tab is restored (`FileEditor.isValid() == false` if the data source is gone), otherwise you
get zombie tabs that fail on first refresh.

**Also rejected:** hosting your panel inside a `TextEditor` tab (the "custom FileEditor inside a
TextEditor" idea). `TextEditorProvider` is a closed implementation over `EditorFactory`; there is no
supported way to swap the text editor's component. If you need a text-based DDL view next to the
structure view, the idiomatic move is a **second `FileEditorProvider`** for the same file with
`PLACE_AFTER_DEFAULT_EDITOR` and a separate editor type id — or a single editor with inner tabs
(what DataGrip does, §5).

---

## 5. How DataGrip itself does it (informative, not reusable)

Class inventory from `Contents/plugins/DatabaseTools/lib/modules/intellij.database.impl.jar` **[V]** —
the package `com.intellij.database.editor` contains, among others:
`DatabaseCompositeTableEditor`, `DatabaseCompositeTableEditorProvider`,
`ExperimentalDatabaseCompositeTableEditorProvider`, `DatabaseTableFileEditor`,
`DatabaseTableFileEditorProvider`, `DasGridFileEditor`, `DasGridFileEditorProvider`,
`FragmentTableFileEditorProvider`, `CsvTableFileEditorProvider`, **`TableStructureFileEditor`**,
**`TableStructureEditorModel`**, **`TableStructureEditorBootstrapper`**, `TableStructureFileEditorState`,
`StructureRecoveryState`, `TableStructureEditorModel`, plus the `Open*FileDescriptor` family
(`OpenDataFileDescriptor`, `OpenDdlFileDescriptor`, **`OpenStructureFileDescriptor`**).

**Structure is an inner tab of a custom `FileEditor`, not a separate editor tab.**

```java
// [V] intellij.database.impl.jar
public final class com.intellij.database.editor.DatabaseCompositeTableEditor
        extends UserDataHolderBase
        implements NavigatableFileEditor, DataHookupContainer, DataGridContainer {
  public DatabaseCompositeTableEditor(Project, VirtualFile, VirtualFile, CoroutineScope, Editors);
  public JComponent getComponent(); public String getName();
  public FileEditorState getState(FileEditorStateLevel); public void setState(FileEditorState);
  public boolean isModified(); public boolean isValid();
}
final class com.intellij.database.editor.EditorTab extends Enum<EditorTab> {   // package-private!
  DATA_EDITOR, STRUCTURE_EDITOR, CODE_EDITOR
}
public final class com.intellij.database.editor.TableStructureFileEditor implements FileEditor {
  public TableStructureFileEditor(VirtualFile, CoroutineScope, TableStructureEditorModel);
  public JComponent getComponent(); public String getName(); public boolean isModified();
  public void dispose(); public void setState(FileEditorState); public FileEditorState getState(FileEditorStateLevel);
}
public final class com.intellij.database.editor.OpenStructureFileDescriptor extends OpenFileDescriptor {
  public OpenStructureFileDescriptor(Project, VirtualFile);
}
```

`DatabaseCompositeTableEditor` holds a `TableStructureFileEditor` field, a `TableStructureEditorModel`,
and `EditorTab.STRUCTURE_EDITOR` / `isStructureEditorSelectedOnce` flags **[V]**; `OpenStructureFileDescriptor`
is created and `.navigate(true)`-d from `com.intellij.database.view.DbNavigationUtilsKt` **[V]** — i.e.
DataGrip "opens structure" by navigating an **existing** composite table-editor tab to its structure
inner tab. `EditorTab` being package-private is a good sign this is deliberately not extensible.

**The file those editors live on is a custom-VFS file:**

```xml
<!-- DatabaseTools/lib/database-plugin.jar : META-INF/plugin.xml [X] -->
<virtualFileSystem key="das" implementationClass="com.intellij.database.vfs.DatabaseVirtualFileSystem"/>
<fileType name="Database Element" implementationClass="com.intellij.database.vfs.DatabaseElementFileType" fieldName="INSTANCE"/>
```

```java
// [V] intellij.database.core.impl.jar
public final class com.intellij.database.vfs.DatabaseVirtualFileSystem
        extends com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem<VirtualFile> {
  static final String PROTOCOL;                       // "das"
  public static DatabaseVirtualFileSystem getDatabaseFileSystem();
  public static FileDesc parseUrl(String);
  public static String getPath(String dataSourceId, ObjectPath path, ObjectKind kind, boolean src);
}
```

So DataGrip's tab URL is `das://<dataSourceId>/<objectPath>…` and it is restored by exactly the
mechanism described in §3 (its `findFileByPathInner` re-creates the element file from the path).

**`FileEditorProvider` registrations in `database-plugin.jar!/META-INF/plugin.xml` [X]** (all three):

```xml
<!-- DatabaseTools/lib/database-plugin.jar!/META-INF/plugin.xml -->
<fileEditorProvider implementation="com.intellij.database.editor.ExperimentalDatabaseCompositeTableEditorProvider" />
<fileEditorProvider implementation="com.intellij.database.editor.DasGridFileEditorProvider" />
<fileEditorProvider implementation="com.intellij.database.editor.FragmentTableFileEditorProvider" />
```

A fourth, sibling registration lives in the grid plugin (`plugins/grid-core-plugin/lib/grid-core-plugin.jar!/META-INF/plugin.xml`)
and is a real-world example of the `isDocumentRequired` attribute **[X]**:

```xml
<fileEditorProvider id="csv-data-editor"
                    implementation="com.intellij.database.editor.CsvTableFileEditorProvider"
                    isDocumentRequired="true" />
```

* no `<textEditorProvider>` registrations in that descriptor;
* `DatabaseTableFileEditorProvider` exists (`public final`, extends `WeighedFileEditorProvider`) but is
  **not registered** anywhere in the installation → legacy/unused, do not build on it;
* `ExperimentalDatabaseCompositeTableEditorProvider` is `FileEditorProvider + DumbAware` (policy
  `HIDE_DEFAULT_EDITOR`-style composite), the other two extend `WeighedFileEditorProvider`.

**Extension points that could let a third party contribute a tab/page to the table data editor: none.**
The descriptor declares **87** extension points (full parsed list extracted); *no* name matches
`tab`, `page` or `dataEditor`. The closest ones are not tab contributors:

| EP | interface / class | what it is |
|---|---|---|
| `com.intellij.database.objectEditorFactory` | `com.intellij.database.schemaEditor.DbmsObjectEditorFactory` | per-DBMS factory for the *schema object editor* (the structure form model), not a tab |
| `com.intellij.database.objectEditorModelFactory` | `…DbmsObjectEditorModelFactory` | same, model side |
| `com.intellij.database.gridHelper`, `…gridColumnsManagerFactory` | grid internals | data grid behaviour, not tabs |
| `com.intellij.database.dataProducer` / `dataConsumer` / `dataImporter` / `dataAuditor` | data plumbing | not tabs |
| `com.intellij.database.selectInProvider` | SelectIn target | "Select in …" only |

(There is also `com.intellij.database.editor.TableEditorCustomGridToolbarProvider` **class** — internal,
**no** extension point registers it **[X]**: it never appears in any plugin.xml.)

**Action group ids relevant to opening things from the database tree [X]** (from
`database-plugin.jar!/META-INF/plugin.xml`, `<group id=…>` / `<add-to-group group-id=…>`):

* Database tool window: **`DatabaseViewPopupMenu`**, **`DatabaseViewToolbar`**, `DatabaseView.Tools`,
  `DatabaseView.NewGroup`, `DatabaseView.AddActionGroupPopup`, `DatabaseView.AddDataSourceGroup`,
  `DatabaseView.Ddl.AddObject`, `DatabaseView.Clipboard`, `DatabaseView.Diagnostics`,
  `DatabaseView.MoreOptions`, `DatabaseView.ViewOptions`, `DatabaseView.Diagrams`,
  `DatabaseView.SetColorItemGroup`, `DatabaseView.LinkedDataSource.ChooseDataSource`
* Editor side: **`Database.EditorTabPopupMenu`**, `EditorTabPopupMenu`, `EditorContextBarMenu`,
  `EditorPopupMenu.GoTo`, `EditorPopupMenu2`
* Data grid / console: `Console.TableResult.*` (`PopupGroup`, `Group`, `Options`, `GoTo`,
  `NavigationAndEditing.Group`, `FloatingToolbarGroup`, `CellActions`, `EditMaximized.Value.Group`,
  `ChooseExtractor.Group`), `Console.InEditorTableResult.{Horizontal,Vertical}.Group`,
  `Console.EditorTableResult.Group`, `Console.Jdbc.Right`
* Keymap: `Database.KeymapGroup.General`, `Database.KeymapGroup.Hidden`

DataGrip's own "open data / open DDL" actions are `Jdbc.OpenEditor.Data` / `Jdbc.OpenEditor.DDL` /
`Jdbc.OpenEditor.Grid.DDL` (`com.intellij.database.actions.OpenEditorAction$…`). They are placed into
`DatabaseViewPopupMenu`/`DatabaseViewToolbar` with `<reference ref="…"/>` (the group belongs to the same
plugin). A third-party plugin must instead use `<add-to-group group-id="DatabaseViewPopupMenu" …/>`,
which works on any registered group id.

**Selection data key for an action [V]:** `com.intellij.database.DatabaseDataKeysCore` exposes

```java
DataKey<com.intellij.database.model.basic.BasicElement> DATABASE_EDITOR_OBJECT;
DataKey<com.intellij.database.psi.DbElement>           DB_EDITOR_OBJECT;
Key<com.intellij.database.psi.DbDataSource>           DATA_SOURCE_KEY;
Key<SearchPath>                                       SEARCH_PATH_KEY;
```

so `e.getData(DatabaseDataKeysCore.DB_EDITOR_OBJECT)` is the intended way to get the selected DB object
from the tree/console context (fall back to `CommonDataKeys.SELECTED_NODES` /
`LangDataKeys.PSI_ELEMENT` where the DG key is absent). Caveat: `com.intellij.database.*` is DataGrip's
internal API — it is not covered by any compatibility guarantee (§10).

---

## 6. Recommendation (short)

1. **Option A** — `TableStructureVirtualFile extends LightVirtualFile` + `TableStructureFileType` +
   `TableStructureFileEditorProvider` (`HIDE_DEFAULT_EDITOR`, `DumbAware`) +
   `TableStructureFileEditor` (JPanel with inner `JTabbedPane`: Columns / Indexes / Constraints).
2. One `VirtualFile` instance per table via a project service (`@Service(Service.Level.PROJECT)`).
3. Register `<fileType>` (no extensions! see §7) and `<fileEditorProvider id=… fileType=…>`.
4. Action in `DatabaseViewPopupMenu` / `DatabaseViewToolbar` / `Database.EditorTabPopupMenu`.
5. Refresh from `FileEditor.selectNotify()` + an explicit Refresh button; re-introspect on a
   background thread and update the three table models.
6. If restart persistence becomes a requirement, add option B (custom VFS) — only the virtual-file
   class changes.

---

## 7. Code skeleton (compiled against 2026.2 — see §8)

Files (all compiled, §8): `docs/research/snippets/src/com/example/dbtabs/structure/`
[TableStructureFileType.java](snippets/src/com/example/dbtabs/structure/TableStructureFileType.java),
[TableStructureVirtualFile.java](snippets/src/com/example/dbtabs/structure/TableStructureVirtualFile.java),
[TableStructureFileEditorProvider.java](snippets/src/com/example/dbtabs/structure/TableStructureFileEditorProvider.java),
[TableStructureFileEditor.java](snippets/src/com/example/dbtabs/structure/TableStructureFileEditor.java),
[TableStructureService.java](snippets/src/com/example/dbtabs/structure/TableStructureService.java),
[OpenTableStructureAction.java](snippets/src/com/example/dbtabs/structure/OpenTableStructureAction.java),
[TableKey.java](snippets/src/com/example/dbtabs/structure/TableKey.java).

### 7.1 File type

```java
public final class TableStructureFileType implements FileType {
  public static final String NAME = "DataGripTableStructure";       // referenced from plugin.xml
  public static final TableStructureFileType INSTANCE = new TableStructureFileType();
  private TableStructureFileType() { }

  @Override public String getName() { return NAME; }
  @Override public String getDescription() { return "Database table structure"; }
  @Override public String getDefaultExtension() { return "tablestructure"; }
  @Override public Icon getIcon() { return AllIcons.Nodes.DataTables; }   // verified to exist
  @Override public boolean isBinary() { return true; }   // no Document is created for the file
}
```

### 7.2 Virtual file (option A)

```java
public final class TableStructureVirtualFile extends LightVirtualFile {
  private final TableKey key;
  public TableStructureVirtualFile(TableKey key) {
    super(key.tabTitle(), TableStructureFileType.INSTANCE, "");   // title == tab text
    this.key = key;
    setWritable(false);
  }
  public TableKey getKey() { return key; }
}
```

### 7.3 Editor

```java
public final class TableStructureFileEditor extends UserDataHolderBase implements FileEditor {
  private final TableStructureVirtualFile file;
  private final JPanel component = new JPanel(new BorderLayout());
  private final JTabbedPane tabs = new JTabbedPane();
  private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

  public TableStructureFileEditor(TableStructureVirtualFile file) {
    this.file = file;
    tabs.addTab("Columns", …); tabs.addTab("Indexes", …); tabs.addTab("Constraints", …);
    component.add(tabs, BorderLayout.CENTER);
  }

  @Override public JComponent getComponent() { return component; }
  @Override public JComponent getPreferredFocusedComponent() { return tabs; }
  @Override public String getName() { return "Structure"; }        // editor kind, not tab title
  @Override public VirtualFile getFile() { return file; }          // MUST override (see §1.2)
  @Override public void setState(FileEditorState state) { }        // getState() default = INSTANCE
  @Override public boolean isModified() { return false; }
  @Override public boolean isValid() { return file.isValid(); }
  @Override public void addPropertyChangeListener(PropertyChangeListener l) { changeSupport.addPropertyChangeListener(l); }
  @Override public void removePropertyChangeListener(PropertyChangeListener l) { changeSupport.removePropertyChangeListener(l); }
  @Override public void selectNotify() { refresh(); }              // refresh on tab activation
  @Override public void dispose() { /* drop models, remove listeners */ }
}
```

### 7.4 Provider

```java
public final class TableStructureFileEditorProvider implements FileEditorProvider, DumbAware {
  public static final String EDITOR_TYPE_ID = "datagrip-table-structure";

  @Override public boolean accept(Project project, VirtualFile file) { return file instanceof TableStructureVirtualFile; }
  @Override public boolean acceptRequiresReadAction() { return false; }        // pure instanceof
  @Override public FileEditor createEditor(Project project, VirtualFile file) { return new TableStructureFileEditor((TableStructureVirtualFile) file); }
  @Override public String getEditorTypeId() { return EDITOR_TYPE_ID; }
  @Override public FileEditorPolicy getPolicy() { return FileEditorPolicy.HIDE_DEFAULT_EDITOR; }
}
```

### 7.5 Opening from an action

```java
public final class OpenTableStructureAction extends DumbAwareAction {
  @Override public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    TableKey key = selectedTableKey(e);            // e.getData(DatabaseDataKeysCore.DB_EDITOR_OBJECT) -> key
    if (project == null || key == null) return;
    TableStructureVirtualFile file = TableStructureService.getInstance(project).fileFor(key);
    FileEditorManager.getInstance(project).openFile(file, true);
  }
  @Override public void update(AnActionEvent e) { e.getPresentation().setEnabledAndVisible(…); }
  @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
}
```

### 7.6 Service (one VirtualFile per table)

```java
@Service(Service.Level.PROJECT)
public final class TableStructureService {
  private final Map<String, TableStructureVirtualFile> files = new ConcurrentHashMap<>();
  public static TableStructureService getInstance(Project project) { return project.getService(TableStructureService.class); }
  public TableStructureVirtualFile fileFor(TableKey key) {
    return files.computeIfAbsent(key.id(), id -> new TableStructureVirtualFile(key));
  }
  public void invalidate(TableKey key) { /* remove + setValid(false) + closeFile */ }
}
```

### 7.7 plugin.xml

```xml
<idea-plugin>
  <depends>com.intellij.database</depends>   <!-- DataGrip -->

  <extensions defaultExtensionNs="com.intellij">
    <!-- 1. FileType: NO extensions= / fileNames= to avoid stealing other files' extensions -->
    <fileType name="DataGripTableStructure"
              implementationClass="com.example.dbtabs.structure.TableStructureFileType"
              fieldName="INSTANCE"/>

    <!-- 2. Editor provider. id= becomes the editor type id; fileType= pre-filters accept() -->
    <fileEditorProvider implementation="com.example.dbtabs.structure.TableStructureFileEditorProvider"
                        id="datagrip-table-structure"
                        fileType="DataGripTableStructure"
                        isDocumentRequired="false"/>

    <!-- 3. one VirtualFile instance per table -->
    <projectService serviceImplementation="com.example.dbtabs.structure.TableStructureService"/>
  </extensions>

  <actions>
    <action id="DatabaseView.OpenTableStructureTab"
            class="com.example.dbtabs.structure.OpenTableStructureAction"
            text="Open Table Structure in Editor"
            description="Shows columns, indexes and constraints in an editor tab"
            icon="AllIcons.Nodes.DataTables">
      <add-to-group group-id="DatabaseViewPopupMenu"  anchor="last"/>
      <add-to-group group-id="DatabaseViewToolbar"    anchor="last"/>
      <add-to-group group-id="Database.EditorTabPopupMenu" anchor="last"/>
      <keyboard-shortcut keymap="$default" first-keystroke="control alt shift S"/>
    </action>
  </actions>
</idea-plugin>
```

* `<fileType>` — **not** `<fileTypeFactory>` (`FileTypeFactory` is `@Deprecated` **[V]**). `fieldName="INSTANCE"`
  is mandatory for the bean unless the class has a public no-arg constructor.
* `fileType="…"` on `<fileEditorProvider>` must match `FileType.getName()` **[V]**
  (`FileEditorProviderManagerImplKt.getFileType()` + `isAcceptedByFileType()`).
* There is **no** `order`/`policy` attribute; `policy` comes from `getPolicy()` **[V]**.
* For option B add:
  `<virtualFileSystem key="dgstructure" implementationClass="com.example.dbtabs.structure.vfs.TableStructureVirtualFileSystem"/>`
  (and **omit** `physical`, i.e. non-physical).

### 7.8 Option B skeleton (compiled, `docs/research/snippets/src/.../vfs/`)

```java
public final class TableStructureVirtualFileSystem extends DummyCachingFileSystem<TableStructureFsFile> {
  public static final String PROTOCOL = "dgstructure";
  public TableStructureVirtualFileSystem() { super(PROTOCOL); }          // MUST be public no-arg
  @Override protected TableStructureFsFile findFileByPathInner(String path) {
    return path.isEmpty() ? null : new TableStructureFsFile(this, path);  // path encodes the table key
  }
  @Override protected String getPresentableUrl(VirtualFile file) { return file.getPath(); }
}

public final class TableStructureFsFile extends VirtualFile {
  // getName/getFileSystem/isWritable/isDirectory/isValid/getParent/getChildren/
  // getOutputStream(throws IOException)/contentsToByteArray(empty)/getTimeStamp/getLength/
  // getInputStream(empty)/refresh(no-op) + getFileType() -> TableStructureFileType.INSTANCE
}
```

`getParent()` returns `null` (flat namespace) — the same shape DataGrip uses for `das://` files.

---

## 8. Compile verification (done, not just claimed)

DataGrip bundles JBR 25, so the snippets were compiled against the real jars:

```bash
JAVAC=~/Applications/DataGrip.app/Contents/jbr/Contents/Home/bin/javac   # javac 25.0.4
CP=$(ls ~/Applications/DataGrip.app/Contents/lib/*.jar | tr '\n' ':')$(ls ~/Applications/DataGrip.app/Contents/plugins/DatabaseTools/lib/*.jar | tr '\n' ':')$(ls ~/Applications/DataGrip.app/Contents/plugins/DatabaseTools/lib/modules/*.jar | tr '\n' ':')
$JAVAC -Xlint:deprecation -proc:none -d /tmp/skel -cp "$CP" $(find docs/research/snippets/src -name '*.java')
```

Result: **exit 0, no errors, no deprecation warnings** (both option A and option B sources, 552 lines
total). Plain `/usr/bin/javac` (21) cannot be used: these jars are class-file version 69 (Java 25).

---

## 9. Pitfalls

1. **Tab restoration (option A)** — verified dead end: state *is* persisted (`url`, `protocol`,
   per-provider state, `managingFsCreationTimestamp`), but restore resolves through
   `FileIdAdapter`/`VirtualFileManager.findFileByUrl`; `mock://` has no registered VFS ⇒ `null` ⇒
   `LOG.error` and the tab disappears. Plan for it (remember keys in a `@State`, re-open on demand).
2. **`FileEditorManagerEx` package move** — `com.intellij.openapi.fileEditor.ex.FileEditorManagerEx`;
   the old `…impl.FileEditorManagerEx` is gone in 2026.2. Code copied from older tutorials won't compile.
3. **`createFileEditor` does not exist on `FileEditorProvider`** — it is `createEditor`. The only
   `createFileEditor` is the Kotlin-`suspend` one on `AsyncFileEditorProvider`.
4. **`FileEditor.getFile()` must be overridden** — the default logs a
   "proper @NotNull implementation required" plugin error (`PluginException.reportDeprecatedDefault`).
5. **`HIDE_DEFAULT_EDITOR` + non-dumb-aware = logged `PluginException`** (checked in
   `FileEditorProviderManagerImplKt.checkPolicy`). Always implement `DumbAware` for such providers.
6. **Duplicate tabs** — `openFile` is keyed by `VirtualFile` identity. Two `LightVirtualFile`s with the
   same name ⇒ two tabs. Cache one instance per table (project service) and invalidate it on
   rename/drop. Also close the tab (`FileEditorManager.closeFile`) before `setValid(false)`, otherwise
   a stale tab can stay visible.
7. **`LightVirtualFile` disposal / leaks** — the file and your `FileEditor` are referenced by the editor
   manager until the tab is closed. Never keep them in a static map keyed by nothing; a
   `@Service(Service.Level.PROJECT)` map dies with the project, and `dispose()` must drop the table
   models and the introspection callback. Do not `Disposer.register(project, file)` — the file is not a
   `Disposable`; register your own `Disposable` instead.
8. **File-type pollution** — a `<fileType>` with `extensions="sql"` (or any real extension) hijacks every
   such file in the IDE. Declare **no** `extensions`/`fileNames`/`patterns` for a synthetic type; the
   files are only created programmatically and `VirtualFileWithAssignedFileType` makes the assigned type
   win. Pick a unique `name` (`DataGripTableStructure`) — the name is a global identifier.
9. **`isBinary() == true`** keeps a `Document` from being created (good: no text editor, no
   `isDocumentRequired`). If you later want editable text, flip it and expect the text editor provider to
   become applicable unless you keep `HIDE_DEFAULT_EDITOR`.
10. **Refresh** — the platform will not poll your table. Use `selectNotify()` + explicit Refresh +
    `FileEditorManager.updateFilePresentation(file)` when the title/icon changes. Re-introspect on a
    background thread (`ReadAction`/`ProgressManager`), never on EDT; guard against a disposed editor
    (`isValid()`), and cancel pending introspection in `dispose()`.
11. **`accept()` is hot** — called for every open file for every provider; keep it an `instanceof`/user-data
    check, and only return `acceptRequiresReadAction() == false` when that is literally true.
12. **Action threading** — 2026.2 requires `getActionUpdateThread()`; `update()` on
    `ActionUpdateThread.EDT` must stay trivial (no DB access). Do the DB lookup in `actionPerformed`.
13. **`LightVirtualFile` lives in `com.intellij.testFramework`** (shipped in production
    `intellij.platform.core.jar`, not annotated `@Deprecated`/`@Internal` **[V]**) — a mild
    "could be moved one day" risk. Option B removes that dependency entirely.
14. **Non-physical VFS (option B)** — no indexing, no `VirtualFileWithId`, no `LocalFileSystem`-only
    actions; `contentsToByteArray()` must return empty rather than throw; `isValid()` must go `false`
    when the table vanishes or you get zombie tabs that error on refresh.

---

## 10. Open questions

1. **Restart persistence: wanted or not?** Auto-restoring structure tabs against a possibly closed
   connection can produce failing tabs at startup. Decide before paying for option B. (If yes: also
   decide whether to restore eagerly, or lazily on first "structure" action of that table.)
2. **Getting the selected table reliably.** `DatabaseDataKeysCore.DB_EDITOR_OBJECT` /
   `DATABASE_EDITOR_OBJECT` exist **[V]** (`com.intellij.database.DatabaseDataKeysCore`, in
   `intellij.database.core.impl.jar`; the `DbElement` type itself is in
   `modules/intellij.database.jar` **[V]**), but `com.intellij.database.*` is an **internal** DataGrip
   API with no compatibility promise. Confirm the class + key against the SDK stubs you compile with,
   and expect breakage on DataGrip upgrades.
3. **Does `fileType="…"` on `<fileEditorProvider>` compare the FileType *name* or a resolved id?**
   Bytecode shows `getCustomAttribute("fileType")` compared in `isAcceptedByFileType`; the exact
   comparison (name vs `FileTypeManager` lookup) should be confirmed empirically with a log line —
   worst case the attribute is simply ignored and `accept()` still filters correctly.
4. **`WeighedFileEditorProvider` ordering** — `MyComparator` calls `getWeight()` **[V]**, but I did not
   trace which collection is sorted at which point (only matters if you ship 2+ providers for the same
   file; use distinct `id`s and explicit `setSelectedEditor(file, editorTypeId)` instead).
5. **`LightVirtualFile` long-term** — no deprecation today; if JetBrains moves it out of
   `platform.core.jar`, option A breaks at compile time (easy fix: own `VirtualFile` subclass, i.e.
   option B without the EP registration).
6. **Where to put the refresh trigger** — `selectNotify()` covers tab activation, but not
   "DDL changed in another tab". A `VirtualFileListener`/`DatabaseModelFacade`-based invalidation
   (`com.intellij.database.modelFacade` EP exists **[X]**) is the proper long-term hook; out of scope here.

---

## Appendix — reproducible commands

```bash
# where is a class?
for j in ~/Applications/DataGrip.app/Contents/lib/*.jar; do
  unzip -l "$j" 2>/dev/null | grep -q "com/intellij/openapi/fileEditor/FileEditorProvider.class" && echo "$j"; done

# signatures
/usr/bin/javap -classpath ~/Applications/DataGrip.app/Contents/lib/intellij.platform.analysis.jar \
  com.intellij.openapi.fileEditor.FileEditorProvider

# deprecation / internal markers
/usr/bin/javap -v -classpath <jar> <class> | grep -iE "Deprecated|ApiStatus|Internal"

# extension points and registrations (all descriptors extracted)
mkdir -p /tmp/dgxml && cd /tmp/dgxml
find ~/Applications/DataGrip.app/Contents/lib ~/Applications/DataGrip.app/Contents/plugins -name '*.jar' \
  | while read j; do for n in $(unzip -Z1 "$j" | grep -E '^META-INF/.*\.xml$'); do
      unzip -p "$j" "$n" > "/tmp/dgxml/$(basename $j)-$n"; done; done
grep -h -o '<virtualFileSystem[^>]*>' /tmp/dgxml/* | sort -u
grep -o '<extensionPoint[^>]*' /tmp/dgxml/database-plugin.jar-META-INF_plugin.xml | wc -l   # 87

# DataGrip's data-editor providers
/usr/bin/javap -classpath ~/Applications/DataGrip.app/Contents/plugins/DatabaseTools/lib/modules/intellij.database.impl.jar \
  com.intellij.database.editor.DasGridFileEditorProvider

# compile check (JBR 25 required)
~/Applications/DataGrip.app/Contents/jbr/Contents/Home/bin/javac -Xlint:deprecation -proc:none \
  -d /tmp/skel -cp "$(ls ~/Applications/DataGrip.app/Contents/lib/*.jar | tr '\n' ':')" \
  $(find docs/research/snippets/src -name '*.java')
```
