package org.pitest.mutationtest.execute;

import org.pitest.boot.HotSwapAgent;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classinfo.ClassName;
import org.pitest.classpath.ClassPath;
import org.pitest.functional.F3;
import org.pitest.util.Unchecked;

import java.io.IOException;

class HotSwap implements F3<ClassName, ClassLoader, byte[], Boolean> {

  private final ClassByteArraySource byteSource;
  private byte[]                     lastClassPreMutation;
  private ClassName                  lastMutatedClass;
  private ClassLoader                lastUsedLoader;

  HotSwap(final ClassByteArraySource byteSource) {
    this.byteSource = byteSource;
  }

  @Override
  public Boolean apply(final ClassName clazzName, final ClassLoader loader,
                       final byte[] b) {
    Class<?> clazz;
    try {
      restoreLastClass(this.byteSource, clazzName, loader);
      this.lastUsedLoader = loader;
      clazz = Class.forName(clazzName.asJavaName(), false, loader);
      try {
        String clazzFilePath = new ClassPath().getAbsoluteClassPath(clazzName.asJavaName());
        HotSwapAgent.writeMutationToDisc(b, clazzFilePath);
        return HotSwapAgent.hotSwap(clazz, b);
      } catch (IOException e) {
        throw Unchecked.translateCheckedException(e);
      }
    } catch (final ClassNotFoundException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

  private void restoreLastClass(final ClassByteArraySource byteSource,
      final ClassName clazzName, final ClassLoader loader)
          throws ClassNotFoundException {
    if ((this.lastMutatedClass != null)
        && !this.lastMutatedClass.equals(clazzName)) {
      restoreForLoader(this.lastUsedLoader);
      restoreForLoader(loader);
    }

    if ((this.lastMutatedClass == null)
        || !this.lastMutatedClass.equals(clazzName)) {
      this.lastClassPreMutation = byteSource.getBytes(clazzName.asJavaName())
          .get();
    }

    if (this.lastMutatedClass != null && this.lastClassPreMutation != null) {
      try {
        String clazzFilePath = new ClassPath().getAbsoluteClassPath(this.lastMutatedClass.asJavaName());
        HotSwapAgent.writeMutationToDisc(this.lastClassPreMutation, clazzFilePath);
      } catch (IOException e) {
          // swallow
      }
    }

    this.lastMutatedClass = clazzName;
  }

  private void restoreForLoader(ClassLoader loader)
      throws ClassNotFoundException {
    final Class<?> clazz = Class.forName(this.lastMutatedClass.asJavaName(), false,
        loader);
    HotSwapAgent.hotSwap(clazz, this.lastClassPreMutation);


  }

}
