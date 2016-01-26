import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by pip on 17.12.2015.
 */
public class HttpInLoopFinder extends AnAction {

    private static final boolean INERT_STATE = true;
    ArrayList<PsiElement> elementsToChange = new ArrayList<PsiElement>();

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getProject();
        VirtualFile[] virtualFiles = DataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
        final Document document = e.getData(PlatformDataKeys.EDITOR).getDocument();
        if (project == null) {
            return;
        }
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        ArrayList<VirtualFile> allJavaFilesInProject = FileGatherer.getAllJavaFilesInProject(project);
        ArrayList<PsiElement> foundElements = new ArrayList<PsiElement>();
        JavaRecursiveElementVisitor javaRecursiveElementVisitor = new JavaRecursiveElementVisitor() {
            @Override
            public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
            }

            @Override
            public void visitAnnotation(PsiAnnotation annotation) {
                if (annotation.getNameReferenceElement().getText().equalsIgnoreCase("POST")) {
                    PsiElement annotatedElement = annotation.getParent().getParent();
                    if (annotatedElement instanceof PsiMethod) {
                        if (visitSuspiciousElement(annotatedElement)){
                            foundElements.add(annotatedElement);
                        }
                    }
                }
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                try {
                    PsiClass classOfExpression = expression.resolveMethod().getContainingClass();
                    String nameOfMethod = expression.resolveMethod().getName();
                    if ((classOfExpression.getQualifiedName().equalsIgnoreCase("java.net.URL")
                            && nameOfMethod.equalsIgnoreCase("openConnection"))
                            || (classOfExpression.getQualifiedName().equalsIgnoreCase("java.net.URLConnection")
                            && nameOfMethod.equalsIgnoreCase("connect"))
                            || (classOfExpression.getQualifiedName().equalsIgnoreCase("org.apache.http.client.HttpClient")
                            && nameOfMethod.equalsIgnoreCase("execute"))
                            || (classOfExpression.getQualifiedName().equalsIgnoreCase("java.net.Socket")
                            && nameOfMethod.equalsIgnoreCase("getOutputStream"))
                            || (classOfExpression.getQualifiedName().equalsIgnoreCase("com.google.android.gms.ads.AdView")
                            && nameOfMethod.equalsIgnoreCase("loadAd"))) {
                        if (checkIfHasLoopParent(expression)) {
                            foundElements.add(expression);
                        }
                    }

                } catch (Exception e){
                    System.out.println(e);
                }
            }
        };
        for (VirtualFile file : allJavaFilesInProject){
            PsiManager.getInstance(project).findFile(file).accept(javaRecursiveElementVisitor);
        }
        for (PsiElement element : elementsToChange){
            HttpInLoopRefactoring refactoring = new HttpInLoopRefactoring(project, element.getContainingFile().getVirtualFile());
            if (!INERT_STATE){
                refactoring.refactor(element);
            }
        }
    }

    private boolean visitSuspiciousElement(PsiElement annotatedElement) {
        if (annotatedElement instanceof PsiClass && ((PsiClass) annotatedElement).getQualifiedName().equalsIgnoreCase("java.util.TimerTask")){
            return true;
        }
        Collection<PsiReference> usages = findUsages(annotatedElement);
        for (PsiReference ref : usages){
            if (ref instanceof PsiReferenceExpression) {
                PsiElement[] children = ((PsiReferenceExpression) ref).getChildren();
                for (PsiElement child : children) {
                    return checkIfHasLoopParent(child);
                }
            }
        }
        return false;
    }

    private boolean checkIfHasLoopParent(PsiElement element){
        while (element.getParent() != null) {
            if (element instanceof PsiWhileStatement) {
                elementsToChange.add(element);
                return true;
            } else if (element instanceof PsiMethod && visitSuspiciousElement(element)) {
                elementsToChange.add(element);
                return true;
            } else if (element instanceof PsiLocalVariable){
                PsiType psiType = ((PsiLocalVariable) element).getType();
                if (psiType instanceof PsiClassType){
                    String className = ((PsiClassType)psiType).resolve().getQualifiedName();
                    PsiType[] implementsListTypes = ((PsiClassType)psiType).resolve().getImplementsListTypes();
                    ArrayList<String> implementsNames = new ArrayList<String>();
                    for (PsiType type : implementsListTypes){
                        if (type instanceof PsiClassType){
                            implementsNames.add(((PsiClassType) type).resolve().getQualifiedName());
                        }
                    }
                    if (className.equalsIgnoreCase("java.util.TimerTask")
                            || className.equalsIgnoreCase("android.os.AsyncTask")
                            || className.equalsIgnoreCase("java.lang.Runnable")
                            || implementsNames.contains("java.lang.Runnable")){
                        elementsToChange.add(element);
                        return true;
                    }
                }
            }
            element = element.getParent();
        }
        return false;
    }

    private Collection<PsiReference> findUsages(PsiElement element) {
        JavaFindUsagesProvider usagesProvider = new JavaFindUsagesProvider();
        usagesProvider.canFindUsagesFor(element);
        Query<PsiReference> query = ReferencesSearch.search(element);
        Collection<PsiReference> result = query.findAll();
        return result;
    }

    private static void highlightElement(Document document, @NotNull PsiElement element)
    {
        final Project project = element.getProject();
        final FileEditorManager editorManager =
                FileEditorManager.getInstance(project);
        final HighlightManager highlightManager =
                HighlightManager.getInstance(project);
        final EditorColorsManager editorColorsManager =
                EditorColorsManager.getInstance();
        final Editor editor = editorManager.getSelectedTextEditor();
        final EditorColorsScheme globalScheme =
                editorColorsManager.getGlobalScheme();
        final TextAttributes textattributes =
                globalScheme.getAttributes(
                        EditorColors.SEARCH_RESULT_ATTRIBUTES);
//        final PsiElement[] elements = new PsiElement[]{element};
//        highlightManager.addOccurrenceHighlights(
//                editor, elements, textattributes, true, null);
//        final WindowManager windowManager = WindowManager.getInstance();
//        final StatusBar statusBar = windowManager.getStatusBar(project);
//        statusBar.setInfo("Press Esc to remove highlighting");
        int lineNum = document.getLineNumber(element.getTextRange().getStartOffset());
        final TextAttributes textattributes_2 = globalScheme.getAttributes(
                EditorColors.SEARCH_RESULT_ATTRIBUTES);
        editor.getMarkupModel().addLineHighlighter(lineNum, HighlighterLayer.CARET_ROW, textattributes);

        System.out.println(("found something @ " + lineNum));
    }
}
