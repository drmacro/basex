package org.basex.query.expr;

import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;

import org.basex.data.*;
import org.basex.index.*;
import org.basex.index.query.*;
import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.item.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.util.*;

/**
 * This index class retrieves texts and attribute values from the index.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class ValueAccess extends IndexAccess {
  /** Expression. */
  private Expr expr;
  /** Index type. */
  final IndexType itype;

  /**
   * Constructor.
   * @param ii input info
   * @param e index expression
   * @param t access type
   * @param ic index context
   */
  public ValueAccess(final InputInfo ii, final Expr e, final IndexType t,
      final IndexContext ic) {
    super(ic, ii);
    expr = e;
    itype = t;
  }

  @Override
  public NodeIter iter(final QueryContext ctx) throws QueryException {
    NodeIter[] iter = {};
    final Iter ir = ctx.iter(expr);
    for(Item it; (it = ir.next()) != null;) {
      final int s = iter.length;
      final NodeIter[] tmp = new NodeIter[s + 1];
      System.arraycopy(iter, 0, tmp, 0, s);
      iter = tmp;
      iter[s] = index(it.string(info));
    }
    return iter.length == 0 ? new NodeSeqBuilder() : iter.length == 1 ? iter[0] :
      new Union(info, expr).eval(iter);
  }

  /**
   * Returns an index iterator.
   * @param term term to be found
   * @return iterator
   */
  private AxisIter index(final byte[] term) {
    final Data data = ictx.data;

    // access index if term is not too long, and if index exists.
    // otherwise, scan data sequentially
    final IndexIterator ii = term.length <= data.meta.maxlen &&
      (itype == IndexType.TEXT ? data.meta.textindex : data.meta.attrindex) ?
      data.iter(new StringToken(itype, term)) : scan(term);

    return new AxisIter() {
      final byte kind = itype == IndexType.TEXT ? Data.TEXT : Data.ATTR;
      final boolean mem = data.inMemory();

      @Override
      public ANode next() {
        while(ii.more()) {
          final int p = ii.next();
          // main memory instance: check if text is no comment, etc.
          if(!mem || data.kind(p) == kind) return new DBNode(data, p, kind);
        }
        return null;
      }
    };
  }

  /**
   * Returns scan-based iterator.
   * @param val value to be found
   * @return node iterator
   */
  private IndexIterator scan(final byte[] val) {
    return new IndexIterator() {
      final boolean text = itype == IndexType.TEXT;
      final byte kind = text ? Data.TEXT : Data.ATTR;
      final Data data = ictx.data;
      int pre = -1;

      @Override
      public int next() {
        return pre;
      }
      @Override
      public boolean more() {
        while(++pre < data.meta.size) {
          if(data.kind(pre) == kind && eq(data.text(pre, text), val)) return true;
        }
        return false;
      }
    };
  }

  @Override
  public boolean uses(final Use u) {
    return expr.uses(u);
  }

  @Override
  public int count(final Var v) {
    return expr.count(v);
  }

  @Override
  public boolean removable(final Var v) {
    return expr.removable(v);
  }

  @Override
  public Expr remove(final Var v) {
    expr = expr.remove(v);
    return this;
  }

  @Override
  public void plan(final FElem plan) {
    addPlan(plan, planElem(DATA, ictx.data.meta.name, TYP, itype), expr);
  }

  @Override
  public String toString() {
    return (itype == IndexType.TEXT ? Function._DB_TEXT : Function._DB_ATTRIBUTE).get(
        info, Str.get(ictx.data.meta.name), expr).toString();
  }
}
