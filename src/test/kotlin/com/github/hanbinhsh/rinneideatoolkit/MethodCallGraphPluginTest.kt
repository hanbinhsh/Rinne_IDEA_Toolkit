package com.github.hanbinhsh.rinneideatoolkit

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.hanbinhsh.rinneideatoolkit.actions.AnalyzeMethodCallsAction
import com.github.hanbinhsh.rinneideatoolkit.model.GraphMethodVisibility
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNodeKind
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNodeType
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.MethodCallGraph
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceMessageKind
import com.github.hanbinhsh.rinneideatoolkit.services.GraphDataService
import com.github.hanbinhsh.rinneideatoolkit.services.MethodCallAnalyzer
import com.github.hanbinhsh.rinneideatoolkit.services.MapperTableAnalyzer
import com.github.hanbinhsh.rinneideatoolkit.services.MapperTableAnalyzer.MapperColumnAction
import com.github.hanbinhsh.rinneideatoolkit.services.SequenceDiagramAnalyzer

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MethodCallGraphPluginTest : BasePlatformTestCase() {

    fun testAnalyzerBuildsReachableGraphAndOptionalSupplementalNodes() {
        addJavaClass(
            "src/com/example/AController.java",
            """
            package com.example;

            public class AController {
                private final AService service = new AService();
                private final CService cService = new CService();

                void a() {
                    service.as();
                }

                void b() {
                    service.bs();
                }

                void c() {
                    cService.cs();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AService.java",
            """
            package com.example;

            public class AService {
                void as() {
                    getMapper().am();
                }

                void bs() {
                }

                void internalOnly() {
                    new HiddenMapper().hidden();
                }

                AMapper getMapper() {
                    return new AMapper();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AMapper.java",
            """
            package com.example;

            public class AMapper {
                void am() {
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/CService.java",
            """
            package com.example;

            public class CService {
                void cs() {
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/HiddenMapper.java",
            """
            package com.example;

            public class HiddenMapper {
                void hidden() {
                }
            }
            """.trimIndent(),
        )

        val analyzer = project.service<MethodCallAnalyzer>()
        val rootClass = findClass("com.example.AController")

        val reachableGraph = runReadAction {
            analyzer.analyze(rootClass, GraphOptions())
        }
        assertHasNode(reachableGraph, "com.example.AController", "a", GraphNodeType.ROOT)
        assertHasNode(reachableGraph, "com.example.AController", "b", GraphNodeType.ROOT)
        assertHasNode(reachableGraph, "com.example.AController", "c", GraphNodeType.ROOT)
        assertHasNode(reachableGraph, "com.example.AService", "as", GraphNodeType.REACHABLE)
        assertHasNode(reachableGraph, "com.example.AService", "bs", GraphNodeType.REACHABLE)
        assertHasNode(reachableGraph, "com.example.AMapper", "am", GraphNodeType.REACHABLE)
        assertHasNode(reachableGraph, "com.example.CService", "cs", GraphNodeType.REACHABLE)
        assertFalse(reachableGraph.nodes.any { it.classQualifiedName == "com.example.AService" && it.methodName == "getMapper" })
        assertFalse(reachableGraph.nodes.any { it.classQualifiedName == "com.example.AService" && it.methodName == "internalOnly" })
        assertHasEdge(reachableGraph, "com.example.AController#a()", "com.example.AService#as()")
        assertHasEdge(reachableGraph, "com.example.AService#as()", "com.example.AMapper#am()")

        val graphWithSupplementals = runReadAction {
            analyzer.analyze(rootClass, GraphOptions(showUnreachedMethods = true))
        }
        assertHasNode(graphWithSupplementals, "com.example.AService", "internalOnly", GraphNodeType.SUPPLEMENTAL)
        assertFalse(graphWithSupplementals.nodes.any { it.classQualifiedName == "com.example.HiddenMapper" && it.methodName == "hidden" })

        val graphWithSupplementalCalls = runReadAction {
            analyzer.analyze(
                rootClass,
                GraphOptions(
                    showUnreachedMethods = true,
                    showCallsFromUnreachedMethods = true,
                ),
            )
        }
        assertHasNode(graphWithSupplementalCalls, "com.example.HiddenMapper", "hidden", GraphNodeType.SUPPLEMENTAL)
        assertHasEdge(graphWithSupplementalCalls, "com.example.AService#internalOnly()", "com.example.HiddenMapper#hidden()")

        val graphWithAccessors = runReadAction {
            analyzer.analyze(rootClass, GraphOptions(showAccessorMethods = true))
        }
        assertHasNode(graphWithAccessors, "com.example.AService", "getMapper", GraphNodeType.REACHABLE)
        assertHasEdge(graphWithAccessors, "com.example.AService#as()", "com.example.AService#getMapper()")
        assertHasEdge(graphWithAccessors, "com.example.AService#getMapper()", "com.example.AMapper#am()")
    }

    fun testAnalyzerHandlesCyclesAndInterfaceTargets() {
        addJavaClass(
            "src/com/example/LoopController.java",
            """
            package com.example;

            public class LoopController {
                void start() {
                    new LoopService().one();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/LoopService.java",
            """
            package com.example;

            public class LoopService {
                void one() {
                    two();
                }

                void two() {
                    one();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/CheckoutController.java",
            """
            package com.example;

            public class CheckoutController {
                void checkout(PaymentService service) {
                    service.pay();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/PaymentService.java",
            """
            package com.example;

            public interface PaymentService {
                void pay();
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/PaymentServiceImpl.java",
            """
            package com.example;

            public class PaymentServiceImpl implements PaymentService {
                @Override
                public void pay() {
                    new PaymentMapper().insert();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/PaymentMapper.java",
            """
            package com.example;

            public class PaymentMapper {
                void insert() {
                }
            }
            """.trimIndent(),
        )

        val analyzer = project.service<MethodCallAnalyzer>()

        val loopGraph = runReadAction {
            analyzer.analyze(findClass("com.example.LoopController"), GraphOptions())
        }
        assertEquals(1, loopGraph.nodes.count { it.classQualifiedName == "com.example.LoopService" && it.methodName == "one" })
        assertEquals(1, loopGraph.nodes.count { it.classQualifiedName == "com.example.LoopService" && it.methodName == "two" })
        assertHasEdge(loopGraph, "com.example.LoopController#start()", "com.example.LoopService#one()")
        assertHasEdge(loopGraph, "com.example.LoopService#one()", "com.example.LoopService#two()")
        assertHasEdge(loopGraph, "com.example.LoopService#two()", "com.example.LoopService#one()")

        val interfaceGraph = runReadAction {
            analyzer.analyze(findClass("com.example.CheckoutController"), GraphOptions())
        }
        assertHasNode(interfaceGraph, "com.example.PaymentServiceImpl", "pay", GraphNodeType.REACHABLE)
        assertHasNode(interfaceGraph, "com.example.PaymentMapper", "insert", GraphNodeType.REACHABLE)
        assertHasEdge(interfaceGraph, "com.example.CheckoutController#checkout(PaymentService)", "com.example.PaymentServiceImpl#pay()")
        assertHasEdge(interfaceGraph, "com.example.PaymentServiceImpl#pay()", "com.example.PaymentMapper#insert()")
    }

    fun testAnalyzerIgnoresExternalMethodsAndActionStoresGraphState() {
        val psiFile = addJavaClass(
            "src/com/example/ExternalController.java",
            """
            package com.example;

            public class ExternalController {
                void call() {
                    System.out.println("x");
                    helper();
                }

                void helper() {
                }
            }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        val analyzer = project.service<MethodCallAnalyzer>()
        val graph = runReadAction {
            analyzer.analyze(findClass("com.example.ExternalController"), GraphOptions())
        }
        assertFalse(graph.nodes.any { it.methodName == "println" })
        assertHasEdge(graph, "com.example.ExternalController#call()", "com.example.ExternalController#helper()")

        val action = AnalyzeMethodCallsAction()
        val event = createEvent(psiFile)
        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)

        action.actionPerformed(event)
        val state = project.service<GraphDataService>().getState()
        assertNotNull(state.graph)
        assertEquals("ExternalController", state.rootClassDisplayName)
    }

    fun testAnalyzerSupportsPrivateMethodVisibilityFiltering() {
        addJavaClass(
            "src/com/example/PrivateController.java",
            """
            package com.example;

            public class PrivateController {
                public void entry() {
                    hidden();
                }

                private void hidden() {
                    new PrivateService().serve();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/PrivateService.java",
            """
            package com.example;

            public class PrivateService {
                public void serve() {
                }
            }
            """.trimIndent(),
        )

        val analyzer = project.service<MethodCallAnalyzer>()
        val rootClass = findClass("com.example.PrivateController")

        val graph = runReadAction {
            analyzer.analyze(rootClass, GraphOptions())
        }
        val publicEntry = graph.nodes.first { it.classQualifiedName == "com.example.PrivateController" && it.methodName == "entry" }
        val privateHidden = graph.nodes.first { it.classQualifiedName == "com.example.PrivateController" && it.methodName == "hidden" }
        val publicServe = graph.nodes.first { it.classQualifiedName == "com.example.PrivateService" && it.methodName == "serve" }

        assertEquals(GraphMethodVisibility.PUBLIC, publicEntry.visibility)
        assertEquals(GraphMethodVisibility.PRIVATE, privateHidden.visibility)
        assertEquals(GraphMethodVisibility.PUBLIC, publicServe.visibility)

        val graphWithoutPrivateMethods = runReadAction {
            analyzer.analyze(
                rootClass,
                GraphOptions(showPrivateMethods = false),
            )
        }
        assertFalse(graphWithoutPrivateMethods.nodes.any {
            it.classQualifiedName == "com.example.PrivateController" && it.methodName == "hidden"
        })
        assertHasEdge(
            graphWithoutPrivateMethods,
            "com.example.PrivateController#entry()",
            "com.example.PrivateService#serve()",
        )
    }

    fun testAnalyzerAddsMapperTablesFromXmlMappings() {
        addJavaClass(
            "src/com/example/UserController.java",
            """
            package com.example;

            public class UserController {
                private final UserService service = new UserService();

                public void login() {
                    service.login();
                }

                public void loadProfile() {
                    service.loadProfile();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/UserService.java",
            """
            package com.example;

            public class UserService {
                private final UserMapper mapper = null;

                public void login() {
                    mapper.findUser();
                }

                public void loadProfile() {
                    mapper.loadProfile();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public interface UserMapper {
                SysUser findUser();
                SysUser loadProfile();
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/SysUser.java",
            """
            package com.example;

            public class SysUser {
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <sql id="baseTables">
                    from sys_user u
                    left join user_profile p on p.user_id = u.id
                </sql>

                <select id="findUser" resultType="com.example.SysUser">
                    select u.*
                    <include refid="baseTables"/>
                    where u.username = #{username}
                </select>

                <select id="loadProfile" resultType="com.example.SysUser">
                    select p.*
                    from user_profile p
                    where p.user_id = #{id}
                </select>
            </mapper>
            """.trimIndent(),
        )

        val analyzer = project.service<MethodCallAnalyzer>()
        val rootClass = findClass("com.example.UserController")

        val graphWithoutTables = runReadAction {
            analyzer.analyze(rootClass, GraphOptions())
        }
        assertFalse(graphWithoutTables.nodes.any { it.nodeKind == GraphNodeKind.DATABASE_TABLE })

        val graphWithTables = runReadAction {
            analyzer.analyze(rootClass, GraphOptions(showMapperTables = true))
        }

        assertHasNode(graphWithTables, "com.example.UserMapper", "findUser", GraphNodeType.REACHABLE)
        assertHasNode(graphWithTables, "com.example.UserMapper", "loadProfile", GraphNodeType.REACHABLE)
        assertHasTableNode(graphWithTables, "sys_user", 1)
        assertHasTableNode(graphWithTables, "user_profile", 2)
        assertHasColumnNode(graphWithTables, "sys_user", "id", 1)
        assertHasColumnNode(graphWithTables, "sys_user", "username", 1)
        assertHasColumnNode(graphWithTables, "user_profile", "user_id", 2)
        assertEquals(1, graphWithTables.nodes.count { it.nodeKind == GraphNodeKind.DATABASE_TABLE && it.tableName == "user_profile" })
        assertHasEdge(graphWithTables, "com.example.UserMapper#findUser()", "db:sys_user#id")
        assertHasEdge(graphWithTables, "com.example.UserMapper#findUser()", "db:sys_user#username")
        assertHasEdge(graphWithTables, "com.example.UserMapper#findUser()", "db:user_profile#user_id")
        assertHasEdge(graphWithTables, "com.example.UserMapper#loadProfile()", "db:user_profile#user_id")
    }

    fun testAnalyzerAddsMapperTablesFromAnnotationSql() {
        addJavaClass(
            "src/com/example/AuditController.java",
            """
            package com.example;

            public class AuditController {
                private final AuditService service = new AuditService();

                public void load() {
                    service.load();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AuditService.java",
            """
            package com.example;

            public class AuditService {
                private final AuditMapper mapper = null;

                public void load() {
                    mapper.load();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AuditMapper.java",
            """
            package com.example;

            import org.apache.ibatis.annotations.Select;

            public interface AuditMapper {
                @Select("select * from audit_log where user_id = #{id}")
                AuditLog load();
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AuditLog.java",
            """
            package com.example;

            public class AuditLog {
            }
            """.trimIndent(),
        )

        val analyzer = project.service<MethodCallAnalyzer>()
        val graph = runReadAction {
            analyzer.analyze(findClass("com.example.AuditController"), GraphOptions(showMapperTables = true))
        }

        assertHasTableNode(graph, "audit_log", 1)
        assertHasColumnNode(graph, "audit_log", "user_id", 1)
        assertHasEdge(graph, "com.example.AuditMapper#load()", "db:audit_log#user_id")
    }

    fun testMapperTableAnalyzerExtractsTableColumnsFromXmlMapper() {
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public interface UserMapper {
                SysUser findUser(String username);
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/SysUser.java",
            """
            package com.example;

            public class SysUser {
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <select id="findUser" resultType="com.example.SysUser">
                    select u.id, u.username
                    from sys_user u
                    where u.username = #{username}
                </select>
            </mapper>
            """.trimIndent(),
        )

        val mapperMethod = findClass("com.example.UserMapper").findMethodsByName("findUser", false).single()
        val mappings = runReadAction {
            project.service<MapperTableAnalyzer>().collectTableMappings(mapperMethod)
        }

        assertEquals(1, mappings.size)
        assertEquals("sys_user", mappings.single().tableName)
        assertEquals(setOf("id", "username"), mappings.single().columns)
    }

    fun testMapperTableAnalyzerExtractsTableColumnsFromAnnotationSqlDirectly() {
        addJavaClass(
            "src/com/example/AuditMapper.java",
            """
            package com.example;

            import org.apache.ibatis.annotations.Select;

            public interface AuditMapper {
                @Select("select id, user_id from audit_log where user_id = #{id}")
                AuditLog load();
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AuditLog.java",
            """
            package com.example;

            public class AuditLog {
            }
            """.trimIndent(),
        )

        val mapperMethod = findClass("com.example.AuditMapper").findMethodsByName("load", false).single()
        val mappings = runReadAction {
            project.service<MapperTableAnalyzer>().collectTableMappings(mapperMethod)
        }

        assertEquals(1, mappings.size)
        assertEquals("audit_log", mappings.single().tableName)
        assertEquals(setOf("id", "user_id"), mappings.single().columns)
    }

    fun testMapperTableAnalyzerExtractsWhereColumnsFromCountXmlQuery() {
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public interface UserMapper {
                int countByEmail(String email);
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <select id="countByEmail" resultType="int">
                    SELECT count(1) FROM sys_user WHERE email = #{email}
                </select>
            </mapper>
            """.trimIndent(),
        )

        val mapperMethod = findClass("com.example.UserMapper").findMethodsByName("countByEmail", false).single()
        val mappings = runReadAction {
            project.service<MapperTableAnalyzer>().collectTableMappings(mapperMethod)
        }

        assertEquals(1, mappings.size)
        assertEquals("sys_user", mappings.single().tableName)
        assertEquals(setOf("email"), mappings.single().columns)
    }

    fun testMapperTableAnalyzerExtractsGroupOrderHavingColumns() {
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public interface UserMapper {
                int summarize();
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <select id="summarize" resultType="int">
                    SELECT status, count(id)
                    FROM sys_user
                    GROUP BY status
                    HAVING count(id) > 1
                    ORDER BY create_time DESC
                </select>
            </mapper>
            """.trimIndent(),
        )

        val mapperMethod = findClass("com.example.UserMapper").findMethodsByName("summarize", false).single()
        val mappings = runReadAction {
            project.service<MapperTableAnalyzer>().collectTableMappings(mapperMethod)
        }

        assertEquals(1, mappings.size)
        assertEquals("sys_user", mappings.single().tableName)
        assertEquals(setOf("status", "id", "create_time"), mappings.single().columns)
    }

    fun testMapperTableAnalyzerExtractsColumnActions() {
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public interface UserMapper {
                int updateStatus(Long id, Integer status);
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <update id="updateStatus">
                    update sys_user
                    set status = #{status}, update_time = now()
                    where id = #{id}
                    order by status desc
                </update>
            </mapper>
            """.trimIndent(),
        )

        val mapperMethod = findClass("com.example.UserMapper").findMethodsByName("updateStatus", false).single()
        val mappings = runReadAction {
            project.service<MapperTableAnalyzer>().collectTableMappings(mapperMethod)
        }

        assertEquals(1, mappings.size)
        val mapping = mappings.single()
        assertEquals("sys_user", mapping.tableName)
        assertEquals(setOf("status", "update_time", "id"), mapping.columns)
        assertEquals(setOf(MapperColumnAction.UPDATE, MapperColumnAction.ORDER_BY), mapping.columnActions["status"])
        assertEquals(setOf(MapperColumnAction.UPDATE), mapping.columnActions["update_time"])
        assertEquals(setOf(MapperColumnAction.WHERE), mapping.columnActions["id"])
    }

    fun testMapperTableAnalyzerExtractsColumnsFromXmlWhereTagAndGraphDoesNotPointToTableHeader() {
        addJavaClass(
            "src/com/example/AssessmentScaleMapper.java",
            """
            package com.example;

            import java.util.List;

            public interface AssessmentScaleMapper {
                List<AssessmentScale> adminSelectScales(String keyword, Boolean enabled);
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/AssessmentScale.java",
            """
            package com.example;

            public class AssessmentScale {
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/AssessmentScaleMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.AssessmentScaleMapper">
                <select id="adminSelectScales" resultType="com.example.AssessmentScale">
                    SELECT *
                    FROM assessment_scale
                    <where>
                        <if test="keyword != null and keyword != ''">
                            AND (
                                code LIKE CONCAT('%', #{keyword}, '%')
                                OR name LIKE CONCAT('%', #{keyword}, '%')
                                OR description LIKE CONCAT('%', #{keyword}, '%')
                            )
                        </if>
                        <if test="enabled != null">
                            AND enabled = #{enabled}
                        </if>
                    </where>
                    ORDER BY id DESC
                </select>
            </mapper>
            """.trimIndent(),
        )

        val mapperClass = findClass("com.example.AssessmentScaleMapper")
        val mapperMethod = mapperClass.findMethodsByName("adminSelectScales", false).single()
        val tableMappings = runReadAction {
            project.service<MapperTableAnalyzer>().collectTableMappings(mapperMethod)
        }

        assertEquals(1, tableMappings.size)
        assertEquals("assessment_scale", tableMappings.single().tableName)
        assertEquals(setOf("code", "name", "description", "enabled", "id"), tableMappings.single().columns)

        val graph = runReadAction {
            project.service<MethodCallAnalyzer>().analyze(mapperClass, GraphOptions(showMapperTables = true))
        }
        val mapperNodeId = graph.nodes.first {
            it.classQualifiedName == "com.example.AssessmentScaleMapper" && it.methodName == "adminSelectScales"
        }.id

        assertFalse(graph.edges.any {
            it.fromNodeId == mapperNodeId && it.toNodeId == "db:assessment_scale"
        })
        assertTrue(graph.edges.any { it.fromNodeId == mapperNodeId && it.toNodeId == "db:assessment_scale#id" })
    }

    fun testAnalyzerAddsDatabaseColumnOperationNodes() {
        addJavaClass(
            "src/com/example/UserController.java",
            """
            package com.example;

            public class UserController {
                private final UserService service = new UserService();

                public void list() {
                    service.list();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/UserService.java",
            """
            package com.example;

            public class UserService {
                private final UserMapper mapper = null;

                public void list() {
                    mapper.listByStatus();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public interface UserMapper {
                SysUser listByStatus();
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/SysUser.java",
            """
            package com.example;

            public class SysUser {
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <select id="listByStatus" resultType="com.example.SysUser">
                    select id, status
                    from sys_user
                    where status = #{status}
                    order by id desc
                </select>
            </mapper>
            """.trimIndent(),
        )

        val graph = runReadAction {
            project.service<MethodCallAnalyzer>().analyze(findClass("com.example.UserMapper"), GraphOptions(showMapperTables = true))
        }

        assertHasColumnNode(graph, "sys_user", "status", 1)
        assertHasColumnOperationNode(graph, "sys_user", "status", "select", 1)
        assertHasColumnOperationNode(graph, "sys_user", "status", "where", 1)
        assertHasColumnOperationNode(graph, "sys_user", "id", "select", 1)
        assertHasColumnOperationNode(graph, "sys_user", "id", "order by", 1)
        val mapperNodeId = graph.nodes.first {
            it.classQualifiedName == "com.example.UserMapper" && it.methodName == "listByStatus"
        }.id
        assertTrue(graph.edges.any { it.fromNodeId == mapperNodeId && it.toNodeId == "db:sys_user#status@select" })
        assertTrue(graph.edges.any { it.fromNodeId == mapperNodeId && it.toNodeId == "db:sys_user#status@where" })
    }

    fun testSequenceAnalyzerBuildsScenarioSectionsForMultipleCallers() {
        addJavaClass(
            "src/com/example/AController.java",
            """
            package com.example;

            public class AController {
                void start() {
                    new BService().b1();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/XController.java",
            """
            package com.example;

            public class XController {
                void alt() {
                    new BService().b1();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/BService.java",
            """
            package com.example;

            public class BService {
                void b1() {
                    new CMapper().c1();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/CMapper.java",
            """
            package com.example;

            public class CMapper {
                void c1() {
                }
            }
            """.trimIndent(),
        )

        val analyzer = project.service<SequenceDiagramAnalyzer>()
        val targetMethod = runReadAction {
            findClass("com.example.BService").findMethodsByName("b1", false).single()
        }

        val diagram = runReadAction {
            analyzer.analyze(targetMethod, GraphOptions())
        }

        assertEquals(2, diagram.scenarios.size)
        assertContainsElements(
            diagram.scenarios.map { it.entryMethodDisplayName },
            "AController.start()",
            "XController.alt()",
        )
        diagram.scenarios.forEach { scenario ->
            assertEquals(4, scenario.messages.size)
            assertContainsElements(scenario.participants.map { it.className }, "BService", "CMapper")
            assertTrue(scenario.messages.any {
                it.kind == SequenceMessageKind.CALL &&
                    it.toClassName == "BService" &&
                    it.methodDisplaySignature == "b1()"
            })
            assertTrue(scenario.messages.any {
                it.kind == SequenceMessageKind.CALL &&
                    it.toClassName == "CMapper" &&
                    it.methodDisplaySignature == "c1()"
            })
            assertTrue(scenario.messages.any { it.kind == SequenceMessageKind.RETURN && it.fromClassName == "CMapper" })
        }
    }

    fun testSequenceAnalyzerRespectsFilteringAndPreservesVisibleChain() {
        addJavaClass(
            "src/com/example/SequenceController.java",
            """
            package com.example;

            public class SequenceController {
                public void entry() {
                    hidden();
                }

                private void hidden() {
                    helper();
                }

                public void helper() {
                    getMapper().save();
                }

                Mapper getMapper() {
                    return new Mapper();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/Mapper.java",
            """
            package com.example;

            public class Mapper {
                public void save() {
                }
            }
            """.trimIndent(),
        )

        val analyzer = project.service<SequenceDiagramAnalyzer>()
        val targetMethod = runReadAction {
            findClass("com.example.SequenceController").findMethodsByName("entry", false).single()
        }

        val diagram = runReadAction {
            analyzer.analyze(
                targetMethod,
                GraphOptions(
                    showPrivateMethods = false,
                    showAccessorMethods = false,
                ),
            )
        }

        assertEquals(1, diagram.scenarios.size)
        val messages = diagram.scenarios.single().messages.filter { it.kind == SequenceMessageKind.CALL }
        assertFalse(messages.any { it.methodDisplaySignature == "hidden()" })
        assertFalse(messages.any { it.methodDisplaySignature == "getMapper()" })
        assertTrue(messages.any { it.methodDisplaySignature == "helper()" && it.isSelfCall })
        assertTrue(messages.any { it.methodDisplaySignature == "save()" && it.toClassName == "Mapper" })
    }

    fun testSequenceAnalyzerKeepsNestedArgumentCallsBeforeOuterServiceCall() {
        addJavaClass(
            "src/com/example/LoginController.java",
            """
            package com.example;

            public class LoginController {
                private final UserService userService = new UserService();

                public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
                    return userService.login(request.getUsername(), request.getPassword(), getClientIp(httpRequest));
                }

                private String getClientIp(HttpServletRequest request) {
                    return "";
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/UserService.java",
            """
            package com.example;

            public class UserService {
                public LoginResponse login(String username, String password, String ip) {
                    return new LoginResponse();
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/LoginRequest.java",
            """
            package com.example;

            public class LoginRequest {
                public String getUsername() {
                    return "";
                }

                public String getPassword() {
                    return "";
                }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/LoginResponse.java",
            """
            package com.example;

            public class LoginResponse {
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/HttpServletRequest.java",
            """
            package com.example;

            public class HttpServletRequest {
            }
            """.trimIndent(),
        )

        val analyzer = project.service<SequenceDiagramAnalyzer>()
        val targetMethod = runReadAction {
            findClass("com.example.LoginController").findMethodsByName("login", false).single()
        }

        val diagram = runReadAction {
            analyzer.analyze(
                targetMethod,
                GraphOptions(
                    showAccessorMethods = true,
                    showPrivateMethods = true,
                ),
            )
        }

        val messages = diagram.scenarios.single().messages.filter { it.kind == SequenceMessageKind.CALL }
        assertEquals("getUsername()", messages[0].methodDisplaySignature)
        assertEquals("getPassword()", messages[1].methodDisplaySignature)
        assertEquals("getClientIp(HttpServletRequest)", messages[2].methodDisplaySignature)
        assertEquals("login(String, String, String)", messages[3].methodDisplaySignature)
        assertEquals("LoginController", messages[2].toClassName)
        assertEquals("UserService", messages[3].toClassName)
        assertTrue(diagram.scenarios.single().messages.any {
            it.kind == SequenceMessageKind.CREATE && it.toClassName == "LoginResponse"
        })
        assertTrue(diagram.scenarios.single().messages.any {
            it.kind == SequenceMessageKind.RETURN &&
                it.fromClassName == "LoginResponse" &&
                it.toClassName == "UserService"
        })

        val filteredDiagram = runReadAction {
            analyzer.analyze(
                targetMethod,
                GraphOptions(
                    showAccessorMethods = false,
                    showPrivateMethods = false,
                ),
            )
        }
        assertFalse(filteredDiagram.scenarios.single().participants.any { it.className == "LoginRequest" })
        assertFalse(filteredDiagram.scenarios.single().participants.any { it.className == "HttpServletRequest" })
        assertTrue(filteredDiagram.scenarios.single().participants.any { it.className == "UserService" })
    }

    fun testSequenceAnalyzerAddsMapperTablesAndColumnMessagesWhenEnabled() {
        addJavaClass(
            "src/com/example/UserMapper.java",
            """
            package com.example;

            public class UserMapper {
                public SysUser findUser() { return null; }
            }
            """.trimIndent(),
        )
        addJavaClass(
            "src/com/example/SysUser.java",
            """
            package com.example;

            public class SysUser {
            }
            """.trimIndent(),
        )
        addProjectFile(
            "resources/mappers/UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
                <select id="findUser" resultType="com.example.SysUser">
                    select id, username
                    from sys_user
                    where username = #{username}
                </select>
            </mapper>
            """.trimIndent(),
        )

        val analyzer = project.service<SequenceDiagramAnalyzer>()
        val targetMethod = runReadAction {
            findClass("com.example.UserMapper").findMethodsByName("findUser", false).single()
        }

        val hiddenDiagram = runReadAction {
            analyzer.analyze(targetMethod, GraphOptions(showMapperTables = false))
        }
        assertFalse(hiddenDiagram.scenarios.single().participants.any { it.className == "sys_user" })

        val visibleDiagram = runReadAction {
            analyzer.analyze(targetMethod, GraphOptions(showMapperTables = true))
        }
        val scenario = visibleDiagram.scenarios.single()
        assertTrue(scenario.participants.any { it.className == "sys_user" })
        assertTrue(scenario.messages.any {
            it.kind == SequenceMessageKind.CALL &&
                it.fromClassName == "UserMapper" &&
                it.toClassName == "sys_user" &&
                it.methodDisplaySignature == "id"
        })
        assertTrue(scenario.messages.any {
            it.kind == SequenceMessageKind.CALL &&
                it.fromClassName == "UserMapper" &&
                it.toClassName == "sys_user" &&
                it.methodDisplaySignature == "username"
        })
    }

    private fun addJavaClass(path: String, content: String): PsiFile =
        myFixture.addFileToProject(path, content)

    private fun addProjectFile(path: String, content: String): PsiFile =
        myFixture.addFileToProject(path, content)

    private fun findClass(qualifiedName: String): PsiClass = runReadAction {
        JavaPsiFacade.getInstance(project)
            .findClass(qualifiedName, GlobalSearchScope.projectScope(project))
            ?: error("Class not found: $qualifiedName")
    }

    private fun createEvent(psiFile: PsiFile): AnActionEvent {
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, psiFile)
            .build()
        return AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, Presentation(), dataContext)
    }

    private fun assertHasNode(
        graph: MethodCallGraph,
        classQualifiedName: String,
        methodName: String,
        nodeType: GraphNodeType,
    ) {
        val nodeSummary = graph.nodes.joinToString { "${it.classQualifiedName}.${it.methodName}:${it.nodeType}" }
        assertTrue(
            "Expected node $classQualifiedName.$methodName of type $nodeType. Actual nodes: $nodeSummary",
            graph.nodes.any {
                it.classQualifiedName == classQualifiedName &&
                    it.methodName == methodName &&
                    it.nodeType == nodeType
            },
        )
    }

    private fun assertHasEdge(graph: MethodCallGraph, from: String, to: String) {
        val edgeSummary = graph.edges.joinToString { "${edgeLabel(graph, it.fromNodeId)} -> ${edgeLabel(graph, it.toNodeId)}" }
        assertTrue(
            "Expected edge $from -> $to. Actual edges: $edgeSummary",
            graph.edges.any { edgeLabel(graph, it.fromNodeId) == from && edgeLabel(graph, it.toNodeId) == to },
        )
    }

    private fun assertHasTableNode(
        graph: MethodCallGraph,
        tableName: String,
        sourceCount: Int,
    ) {
        val node = graph.nodes.firstOrNull { it.nodeKind == GraphNodeKind.DATABASE_TABLE && it.tableName == tableName }
        assertNotNull("Expected table node for $tableName", node)
        assertEquals(sourceCount, node?.sourceCount)
        assertEquals(GraphNodeType.DATABASE_TABLE, node?.nodeType)
    }

    private fun assertHasColumnNode(
        graph: MethodCallGraph,
        tableName: String,
        columnName: String,
        sourceCount: Int,
    ) {
        val node = graph.nodes.firstOrNull {
            it.nodeKind == GraphNodeKind.DATABASE_COLUMN &&
                it.tableName == tableName &&
                it.columnName == columnName
        }
        assertNotNull("Expected column node for $tableName.$columnName", node)
        assertEquals(sourceCount, node?.sourceCount)
        assertEquals(GraphNodeType.DATABASE_COLUMN, node?.nodeType)
    }

    private fun assertHasColumnOperationNode(
        graph: MethodCallGraph,
        tableName: String,
        columnName: String,
        actionDisplayName: String,
        sourceCount: Int,
    ) {
        val node = graph.nodes.firstOrNull {
            it.nodeKind == GraphNodeKind.DATABASE_COLUMN_OPERATION &&
                it.tableName == tableName &&
                it.columnName == columnName &&
                it.displaySignature == actionDisplayName
        }
        assertNotNull("Expected column operation node for $tableName.$columnName.$actionDisplayName", node)
        assertEquals(sourceCount, node?.sourceCount)
        assertEquals(GraphNodeType.DATABASE_COLUMN_OPERATION, node?.nodeType)
    }

    private fun edgeLabel(graph: MethodCallGraph, nodeId: String): String {
        val node = graph.nodes.first { it.id == nodeId }
        return "${node.classQualifiedName}#${node.displaySignature}"
    }

    override fun getTestDataPath(): String = "src/test/testData"
}
