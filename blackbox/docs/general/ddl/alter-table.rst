.. _sql_ddl_alter_table:

===============
Altering Tables
===============

.. NOTE::

   ``ALTER COLUMN`` and ``DROP COLUMN`` actions are not currently supported.
   See :ref:`crate_standard_sql`.

.. rubric:: Table of Contents

.. contents::
   :local:

.. hide:

    cr> CREATE TABLE my_table (id LONG);
    CREATE OK, 1 row affected (... sec)

Updating Parameters
===================

The parameters of a table can be modified using the ``ALTER TABLE`` clause::

    cr> alter table my_table set (number_of_replicas = '0-all');
    ALTER OK, -1 rows affected (... sec)

In order to set a parameter to its default value use ``reset``::

    cr> alter table my_table reset (number_of_replicas);
    ALTER OK, -1 rows affected (... sec)

Read :ref:`Alter Partitioned Tables <partitioned_tables_alter>` to see how to
alter parameters of partitioned tables.

Adding Columns
==============

In order to add a column to an existing table use ``ALTER TABLE`` with the
``ADD COLUMN`` clause::

    cr> alter table my_table add column new_column_name string;
    ALTER OK, -1 rows affected (... sec)

The inner schema of object columns can also be extended, as shown in the
following example.

First a column of type object is added::

    cr> alter table my_table add column obj_column object as (age int);
    ALTER OK, -1 rows affected (... sec)

And now a nested column named ``name`` is added to the ``obj_column``::

    cr> alter table my_table add column obj_column['name'] string;
    ALTER OK, -1 rows affected (... sec)

::

    cr> select column_name, data_type from information_schema.columns
    ... where table_name = 'my_table' and column_name like 'obj_%';
    +--------------------+-----------+
    | column_name        | data_type |
    +--------------------+-----------+
    | obj_column         | object    |
    | obj_column['age']  | integer   |
    | obj_column['name'] | string    |
    +--------------------+-----------+
    SELECT 3 rows in set (... sec)

Closing and Opening Tables
==========================

A table can be closed by using ``ALTER TABLE`` with the ``CLOSE`` clause::

    cr> alter table my_table close;
    ALTER OK, -1 rows affected (... sec)

Closing a table will cause all operations beside ``ALTER TABLE ... OPEN`` to
fail.

A table can be reopened again by using ``ALTER TABLE`` with the ``OPEN``
clause::

    cr> alter table my_table open;
    ALTER OK, -1 rows affected (... sec)

.. NOTE::

    This setting is *not* the same as :ref:`table-settings-blocks.read_only`.
    Closing and opening a table will preserve these settings if they are
    already set.

Renaming Tables
===============

A table can be renamed by using ``ALTER TABLE`` with the ``RENAME TO`` clause::

     cr> alter table my_table rename to my_new_table;
     ALTER OK, -1 rows affected (... sec)

During the rename operation the table will be closed, and all operations on the
table will fail until the rename operation is completed.

.. Warning::

    Do not run multiple concurrent rename operations on the same table. The rename
    operation is not atomic.

.. _ddl_reroute_shards:

Reroute Shards
==============

With the ``REROUTE`` command it is possible to control the allocations of
shards. This gives you the ability to re-balance the cluster state manually.
The supported reroute options are listed in the reference documentation of
:ref:`ALTER TABLE REROUTE <alter_table_reroute>`.

Shard rerouting can help solve several problems:

    * **Unassigned shards**: Due to cause of lack of space, shard awareness or
      any other failure that happens during the automatic shard allocation it is
      possible to gain unassigned shards in the cluster.

    * **"Hot Shards"**: Most of your queries affect certain shards only. These
      shards lie on a node that has insufficient resources.

This command takes these :ref:`Routing Allocation Settings <conf_routing>` into
account. Once an allocation occurs CrateDB tries (by default) to re-balance
shards to an even state. CrateDB can be set to disable shard re-balancing
with the setting ``cluster.routing.rebalance.enable=None`` to perform only the
explicit triggered allocations.
.

.. NOTE::

    The command only triggers the allocation and reports back if the process has
    been acknowledged or rejected. Moving or allocating large shards takes more
    time to complete.

In those two cases it may be necessary to move shards manually to another node
or force the retry of the allocation process.
