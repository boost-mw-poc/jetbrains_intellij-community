/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.descriptors;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.AbstractPsiCachedValue;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.PsiCachedValueImpl;
import com.intellij.psi.impl.source.html.dtd.HtmlSymbolDeclaration;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.validation.RngSchemaValidator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.rngom.digested.*;
import org.kohsuke.rngom.nc.NameClass;
import org.kohsuke.rngom.nc.NameClassVisitor;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class RngElementDescriptor implements XmlElementDescriptor {
  private static final @NonNls QName UNKNOWN = new QName("", "#unknown");

  private static final Logger LOG = Logger.getInstance(RngElementDescriptor.class);

  private static final Key<ParameterizedCachedValue<XmlElementDescriptor, RngElementDescriptor>> DESCR_KEY = Key.create("DESCR");
  private static final Key<ParameterizedCachedValue<XmlAttributeDescriptor[], RngElementDescriptor>> ATTRS_KEY = Key.create("ATTRS");

  protected static final XmlElementDescriptor NULL = null;

  private final DElementPattern myElementPattern;
  protected final RngNsDescriptor myNsDescriptor;
  private final AbstractPsiCachedValue<PsiElement> myCachedElement;

  RngElementDescriptor(RngNsDescriptor nsDescriptor, DElementPattern pattern) {
    myNsDescriptor = nsDescriptor;
    myElementPattern = pattern;
    myCachedElement = new PsiCachedValueImpl.Soft<>(nsDescriptor.getDescriptorFile().getManager(), () -> {
      final PsiElement decl = myNsDescriptor.getDeclaration();
      if (decl == null/* || !decl.isValid()*/) {
        return CachedValueProvider.Result.create(null, ModificationTracker.EVER_CHANGED);
      }

      final PsiElement element = getDeclarationImpl(decl, myElementPattern.getLocation());

      return CachedValueProvider.Result.create(element, element.getContainingFile());
    });
  }

  @Override
  public String getQualifiedName() {
    final QName qName = getQName();
    return qName != null ? format(qName, "") : "#unknown";
  }

  @Override
  public String getDefaultName() {
    return getName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    if (context == null) {
      return EMPTY_ARRAY;
    }

    // TODO: Not sure why this is needed. IDEA sometimes asks us for descriptors with a context that doesn't match our
    // element pattern. At least at namespace boundaries...
    final DElementPattern pattern;
    final XmlElementDescriptor descriptor = myNsDescriptor.getElementDescriptor(context);
    if (descriptor instanceof RngElementDescriptor) {
      final DElementPattern p = ((RngElementDescriptor)descriptor).myElementPattern;
      pattern = p != null ? p : myElementPattern;
    } else {
      pattern = myElementPattern;
    }

    final List<DElementPattern> patterns = ChildElementFinder.find(2, pattern);
    return myNsDescriptor.convertElementDescriptors(patterns);
  }

  protected XmlElementDescriptor findElementDescriptor(XmlTag childTag) {
    final List<DElementPattern> patterns = ChildElementFinder.find(2, myElementPattern);
    final XmlElementDescriptor d = myNsDescriptor.findDescriptor(childTag, patterns);
    return d == null ? NULL : d;
  }

  public final XmlElementDescriptor getElementDescriptor(final XmlTag childTag) {
    return getElementDescriptor(childTag, null);
  }

  @Override
  public final XmlElementDescriptor getElementDescriptor(final XmlTag childTag, XmlTag contextTag) {
    final ConcurrentMap<RngElementDescriptor, CachedValue<XmlElementDescriptor>> descriptorMap =
      CachedValuesManager.getCachedValue(
        childTag, () -> CachedValueProvider.Result.create(ContainerUtil.createConcurrentWeakMap(), ModificationTracker.NEVER_CHANGED));
    final XmlElementDescriptor value = descriptorMap.computeIfAbsent(this,  descr -> {
      return CachedValuesManager.getManager(childTag.getProject()).createCachedValue(() -> {
        final XmlElementDescriptor descriptor = descr.findElementDescriptor(childTag);
        return CachedValueProvider.Result.create(descriptor, descr.getDependencies(), childTag);
      });
    }).getValue();
    return value == NULL ? null : value;
  }

  @Override
  public final XmlAttributeDescriptor[] getAttributesDescriptors(final @Nullable XmlTag context) {
    if (context != null) {
      return getCachedValue(context, this, ATTRS_KEY, p -> {
        final XmlAttributeDescriptor[] value = p.collectAttributeDescriptors(context);
        return CachedValueProvider.Result.create(value, p.getDependencies(), context);
      });
    } else {
      return collectAttributeDescriptors(null);
    }
  }

  private static <D extends PsiElement, T, P> T getCachedValue(D context, P p, Key<ParameterizedCachedValue<T, P>> key, ParameterizedCachedValueProvider<T, P> provider) {
    final CachedValuesManager mgr = CachedValuesManager.getManager(context.getProject());
    return mgr.getParameterizedCachedValue(context, key, provider, false, p);
  }

  protected XmlAttributeDescriptor[] collectAttributeDescriptors(@Nullable XmlTag context) {
    return computeAttributeDescriptors(AttributeFinder.find((QName)null, myElementPattern));
  }

  protected XmlAttributeDescriptor[] computeAttributeDescriptors(final Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> map) {
    final MultiMap<QName, DAttributePattern> name2patterns = new MultiMap<>();

    for (DAttributePattern pattern : map.keySet()) {
      for (QName name : pattern.getName().listNames()) {
        name2patterns.putValue(name, pattern);
      }
    }

    return name2patterns.entrySet().stream().map(entry -> {
      var patterns = entry.getValue();
      Map<String, String> values = new LinkedHashMap<>();
      boolean isOptional = false;
      SmartList<Locator> declarations = new SmartList<>();

      var name = UNKNOWN;
      for (DAttributePattern pattern : patterns) {
        if (name == UNKNOWN) {
          var patternName = ContainerUtil.getFirstItem(pattern.getName().listNames());
          if (patternName != null) {
            name = patternName;
          }
        }
        final Pair<? extends Map<String, String>, Boolean> value = map.get(pattern);
        values.putAll(value.first);
        isOptional |= value.second;
        declarations.add(pattern.getLocation());
      }

      return new RngXmlAttributeDescriptor(this, name, values, isOptional, declarations);
    }).toArray(RngXmlAttributeDescriptor[]::new);
  }

  @Override
  public final XmlAttributeDescriptor getAttributeDescriptor(String attributeName, @Nullable XmlTag context) {
    return getAttributeDescriptor("", attributeName);
  }

  @Override
  public final XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getNamespace(), attribute.getLocalName());
  }

  protected XmlAttributeDescriptor getAttributeDescriptor(String namespace, String localName) {
    final QName qname = new QName(namespace, localName);

    return computeAttributeDescriptor(AttributeFinder.find(qname, myElementPattern));
  }

  protected XmlAttributeDescriptor computeAttributeDescriptor(final Map<DAttributePattern, Pair<? extends Map<String, String>, Boolean>> attributes) {
    if (!attributes.isEmpty()) {
      var name = UNKNOWN;
      Map<String, String> values = new LinkedHashMap<>();
      boolean isOptional = false;
      SmartList<Locator> declarations = new SmartList<>();

      for (DAttributePattern pattern : attributes.keySet()) {
        if (name == UNKNOWN) {
          var patternName = ContainerUtil.getFirstItem(pattern.getName().listNames());
          if (patternName != null) {
            name = patternName;
          }
        }
        final Pair<? extends Map<String, String>, Boolean> value = attributes.get(pattern);
        values.putAll(value.first);
        isOptional |= value.second;
        declarations.add(pattern.getLocation());
      }
      return new RngXmlAttributeDescriptor(this, name, values, isOptional, declarations);
    } else {
      return null;
    }
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myNsDescriptor;
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  // is this actually used anywhere?
  @Override
  public int getContentType() {
    final DPattern child = myElementPattern.getChild();
    if (child instanceof DEmptyPattern) {
      return CONTENT_TYPE_EMPTY;
    } else if (child instanceof DTextPattern) {
      return CONTENT_TYPE_MIXED;
    } else if (child instanceof DElementPattern) {
      return ((DElementPattern)child).getName().accept(MyNameClassVisitor.INSTANCE);
    } else {
      return CONTENT_TYPE_CHILDREN;
    }
  }

  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myCachedElement.getValue();
  }

  public PsiElement getDeclaration(Locator location) {
    final PsiElement element = myNsDescriptor.getDeclaration();
    if (element == null) {
      return null;
    }
    return getDeclarationImpl(element, location);
  }

  private static PsiElement getDeclarationImpl(PsiElement decl, Locator location) {
    final VirtualFile virtualFile = RngSchemaValidator.findVirtualFile(location.getSystemId());
    if (virtualFile == null) {
      return decl;
    }

    final Project project = decl.getProject();
    final PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
    if (file == null) {
      return decl;
    }

    return AstLoadingFilter.forceAllowTreeLoading(file, () -> getDeclarationImpl(project, decl, location, file));
  }

  @Override
  public @NonNls String getName(PsiElement context) {
    final QName qName = getQName();
    if (qName == null) {
      return "#unknown";
    }
    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
    final String prefix = xmlTag != null ? xmlTag.getPrefixByNamespace(qName.getNamespaceURI()) : null;
    return format(qName, prefix != null ? prefix : qName.getPrefix());
  }

  @Override
  public @NonNls String getName() {
    final QName qName = getQName();
    if (qName == null) {
      return "#unknown";
    }
    return qName.getLocalPart();
  }

  private @Nullable QName getQName() {
    final Iterator<QName> iterator = myElementPattern.getName().listNames().iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    return iterator.next();
  }

  private static String format(QName qName, String p) {
    final String localPart = qName.getLocalPart();
    return !p.isEmpty() ? p + ":" + localPart : localPart;
  }

  private static @Nullable PsiElement getDeclarationImpl(@NotNull Project project, PsiElement decl, Locator location, PsiFile file) {
    final int column = location.getColumnNumber();
    final int line = location.getLineNumber();

    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    assert document != null;

    if (line <= 0 || document.getLineCount() < line - 1) {
      return decl;
    }
    final int startOffset = document.getLineStartOffset(line - 1);

    final PsiElement at;
    if (column > 0) {
      if (decl.getContainingFile().getFileType() == RncFileType.getInstance()) {
        return new RncLocationPsiElement(file, startOffset, column);
      }
      at = file.findElementAt(startOffset + column - 2);
    } else {
      PsiElement element = file.findElementAt(startOffset);
      at = element != null ? PsiTreeUtil.nextLeaf(element) : null;
    }

    return PsiTreeUtil.getParentOfType(at, XmlTag.class);
  }

  @Override
  public void init(PsiElement element) {

  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RngElementDescriptor that = (RngElementDescriptor)o;

    if (!myElementPattern.equals(that.myElementPattern)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElementPattern.hashCode();
  }

  @Override
  public Object @NotNull [] getDependencies() {
    return myNsDescriptor.getDependencies();
  }

  private static class MyNameClassVisitor implements NameClassVisitor<Integer> {
    public static final MyNameClassVisitor INSTANCE = new MyNameClassVisitor();

    @Override
    public Integer visitAnyName() {
      return CONTENT_TYPE_ANY;
    }

    @Override
    public Integer visitAnyNameExcept(NameClass nc) {
      return CONTENT_TYPE_ANY;
    }

    @Override
    public Integer visitChoice(NameClass nc1, NameClass nc2) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitName(QName name) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitNsName(String ns) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitNsNameExcept(String ns, NameClass nc) {
      return CONTENT_TYPE_CHILDREN;
    }

    @Override
    public Integer visitNull() {
      return CONTENT_TYPE_EMPTY;
    }
  }

  public DElementPattern getElementPattern() {
    return myElementPattern;
  }

  private static final class RncLocationPsiElement extends FakePsiElement implements NavigationItem, HtmlSymbolDeclaration {
    private final PsiFile myFile;
    private final int myStartOffset;
    private final int myColumn;
    private final String myName;
    private final Kind myKind;

    private RncLocationPsiElement(PsiFile file, int startOffset, int column) {
      myFile = file;
      myStartOffset = startOffset;
      myColumn = column;
      PsiElement definition = getNavigationElement();
      myName = definition.getText();
      PsiElement obj = definition.getPrevSibling();
      PsiElement prevPrevSibling = obj == null ? null : obj.getPrevSibling();
      if (prevPrevSibling == null) {
        LOG.error("Failed to locate type for RNC element - " + myName);
      }
      myKind = prevPrevSibling != null && "element".equals(prevPrevSibling.getText()) ? Kind.ELEMENT : Kind.ATTRIBUTE;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public @NotNull HtmlSymbolDeclaration.Kind getKind() {
      return myKind;
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
      final PsiElement rncElement = myFile.findElementAt(myStartOffset + myColumn);
      final ASTNode pattern = rncElement != null ? TreeUtil.findParent(rncElement.getNode(), RncElementTypes.PATTERN) : null;
      final ASTNode nameClass = pattern != null ? pattern.findChildByType(RncElementTypes.NAME_CLASS) : null;
      //noinspection ConstantConditions
      return nameClass != null ? nameClass.getPsi() : rncElement;
    }

    @Override
    public PsiElement getParent() {
      return getNavigationElement();
    }

    @Override
    public PsiFile getContainingFile() {
      return myFile;
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @Override
    public boolean equals(Object another) {
      return another instanceof RncLocationPsiElement &&
             ((RncLocationPsiElement)another).myFile == myFile &&
             ((RncLocationPsiElement)another).myStartOffset == myStartOffset &&
             ((RncLocationPsiElement)another).myColumn == myColumn;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myFile, myStartOffset, myColumn);
    }
  }
}
