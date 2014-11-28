package org.basex.query.func.parallel;

import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.value.item.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class ParallelForEach extends StandardFunc {
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    final int threads = (int) toLong(exprs[1], qc);
    final ValueBuilder[] values = new ValueBuilder[threads];
    final Thread[] evals = new Thread[threads];
    final boolean[] finished = new boolean[threads];

    // create and start threads
    for(int i = 0; i < threads; i++) {
      final int c = i;
      values[c] = new ValueBuilder();
      evals[c] = new Thread() {
        @Override
        public void run() {
          try {
            // cache all values
            final Iter ir = exprs[0].iter(qc);
            for(Item it; (it = ir.next()) != null;) values[c].add(it);
            finished[c] = true;
          } catch(final QueryException ex) {
            throw new QueryRTException(ex);
          }
        }
      };
    }
    for(final Thread e : evals) e.start();

    return new Iter() {
      /** Currently returned values. */
      ValueBuilder vb;
      /** Current thread counter. */
      int c = -1;

      @Override
      public Item next() throws QueryException {
        do {
          if(vb == null) {
            // check if more threads exist
            if(++c == threads) return null;
            // wait until current thread has finished
            while(!finished[c]) Thread.yield();
            // assign next value builder, invalidate original reference
            vb = values[c];
            values[c] = null;
          }
          // return next result or invalidate current builder
          final Item it = vb.next();
          if(it != null) return it;
          vb = null;
        } while(true);
      }
    };
  }
}
