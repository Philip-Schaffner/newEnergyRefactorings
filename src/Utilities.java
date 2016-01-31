import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by pip on 26.01.2016.
 */
public class Utilities {
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

    public static Collection<PsiReference> findUsages(PsiElement element) {
        JavaFindUsagesProvider usagesProvider = new JavaFindUsagesProvider();
        usagesProvider.canFindUsagesFor(element);
        Query<PsiReference> query = ReferencesSearch.search(element);
        Collection<PsiReference> result = query.findAll();
        return result;
    }

    public static @Nullable PsiElement findInParents(PsiElement element, Class type){
        while (!(element.getClass() == type)
            && (element.getParent() != null)){
                element = element.getParent();
            }
        return element;
    }

    public static @Nullable PsiElement findInChildren(PsiElement element, Class elementClass){
        LinkedList<PsiElement> queue = new LinkedList<PsiElement>();
        queue.add(element);
        while (!queue.isEmpty()){
            PsiElement child = queue.remove();
            if (child.getClass() == elementClass){
                return child;
            } else {
                for (PsiElement n : child.getChildren()){
                    queue.add(n);
                }
            }
        }
        return null;
    }

    public static boolean checkIfImportExists(PsiImportStatementBase[] importList, String s) {
        for (PsiImportStatementBase statement : importList){
            if (statement.getText().contains(s)){
                return true;
            }
        }
        return false;
    }

    public static ArrayList<PsiElement> getArguments(PsiExpressionList expressionList){
        return new ArrayList<>();
    }
}