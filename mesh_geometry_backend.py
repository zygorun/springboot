@app.route('/api/models/<model_id>/mesh/geometry', methods=['GET'])
def get_mesh_geometry(model_id):
    """获取网格几何数据用于前端渲染"""
    try:
        if model_id not in current_models:
            return jsonify({'error': '模型不存在'}), 404
        
        model = current_models[model_id]
        
        # 获取组件和网格
        comp_nodes = list((model / 'components').children())
        if not comp_nodes:
            return jsonify({'error': '模型中没有组件'}), 404
        
        comp_tag = comp_nodes[0].tag()
        comp = model.java.component().get(comp_tag)
        mesh_obj = comp.mesh()
        
        # 检查网格是否构建
        try:
            if hasattr(mesh_obj, 'isBuilt') and not mesh_obj.isBuilt():
                return jsonify({'error': '网格未构建，请先生成网格'}), 400
        except:
            pass
        
        # 获取网格数据
        logger.info("开始提取网格几何数据")
        
        # 方法1：尝试直接获取顶点和单元数据
        vertices = []
        indices = []
        
        try:
            # 获取顶点坐标 - 尝试多种方法
            vertex_data = None
            try:
                if hasattr(mesh_obj, 'getVertex'):
                    vertex_data = mesh_obj.getVertex()
                elif hasattr(mesh_obj, 'getVertices'):
                    vertex_data = mesh_obj.getVertices()
                elif hasattr(mesh_obj, 'getCoord'):
                    vertex_data = mesh_obj.getCoord()
            except Exception as v_err:
                logger.warning(f"获取顶点数据失败: {v_err}")
            
            if vertex_data is not None:
                # 转换Java数组为Python列表
                if hasattr(vertex_data, 'toArray'):
                    vertices = vertex_data.toArray().tolist()
                else:
                    # 如果是二维数组 [x[], y[], z[]]
                    if len(vertex_data) >= 3:
                        x_coords = vertex_data[0]
                        y_coords = vertex_data[1]
                        z_coords = vertex_data[2] if len(vertex_data) > 2 else [0] * len(x_coords)
                        
                        # 转换为 [x1,y1,z1, x2,y2,z2, ...] 格式
                        vertices = []
                        for i in range(len(x_coords)):
                            vertices.extend([float(x_coords[i]), float(y_coords[i]), float(z_coords[i])])
                
                logger.info(f"成功获取 {len(vertices)//3} 个顶点")
            
            # 获取单元数据 - 尝试多种方法
            element_data = None
            try:
                if hasattr(mesh_obj, 'getElem'):
                    element_data = mesh_obj.getElem()
                elif hasattr(mesh_obj, 'getElements'):
                    element_data = mesh_obj.getElements()
                elif hasattr(mesh_obj, 'getConnectivity'):
                    element_data = mesh_obj.getConnectivity()
            except Exception as e_err:
                logger.warning(f"获取单元数据失败: {e_err}")
            
            if element_data is not None:
                # 处理单元连接关系
                if hasattr(element_data, 'toArray'):
                    elem_array = element_data.toArray()
                    # 转换四面体单元为三角形面
                    indices = convert_tetrahedra_to_triangles(elem_array)
                else:
                    # 如果是列表格式
                    indices = convert_tetrahedra_to_triangles(element_data)
                
                logger.info(f"成功获取 {len(indices)//3} 个三角面")
            
        except Exception as extract_err:
            logger.error(f"提取网格数据时出错: {extract_err}")
            
            # 方法2：备用方法 - 通过export功能获取
            try:
                logger.info("尝试备用方法获取网格数据")
                
                # 创建临时导出来获取网格数据
                import tempfile
                import os
                
                with tempfile.NamedTemporaryFile(suffix='.txt', delete=False) as tmp_file:
                    tmp_path = tmp_file.name
                
                try:
                    # 尝试导出网格为文本格式
                    model.java.mesh().export().set('filename', tmp_path)
                    model.java.mesh().export().run()
                    
                    # 读取导出的文件
                    vertices, indices = parse_mesh_export_file(tmp_path)
                    logger.info(f"通过导出方法获取了 {len(vertices)//3} 个顶点, {len(indices)//3} 个三角面")
                    
                except Exception as export_err:
                    logger.warning(f"导出方法也失败: {export_err}")
                finally:
                    # 清理临时文件
                    try:
                        os.unlink(tmp_path)
                    except:
                        pass
                        
            except Exception as backup_err:
                logger.warning(f"备用方法失败: {backup_err}")
        
        # 检查是否成功获取数据
        if not vertices or not indices:
            return jsonify({
                'error': '无法提取网格几何数据',
                'vertices_count': len(vertices)//3 if vertices else 0,
                'faces_count': len(indices)//3 if indices else 0
            }), 500
        
        # 计算边界盒用于相机定位
        if vertices:
            x_coords = [vertices[i] for i in range(0, len(vertices), 3)]
            y_coords = [vertices[i] for i in range(1, len(vertices), 3)]
            z_coords = [vertices[i] for i in range(2, len(vertices), 3)]
            
            bounds = {
                'min': [min(x_coords), min(y_coords), min(z_coords)],
                'max': [max(x_coords), max(y_coords), max(z_coords)],
                'center': [
                    (min(x_coords) + max(x_coords)) / 2,
                    (min(y_coords) + max(y_coords)) / 2,
                    (min(z_coords) + max(z_coords)) / 2
                ]
            }
        else:
            bounds = {'min': [0,0,0], 'max': [1,1,1], 'center': [0.5,0.5,0.5]}
        
        return jsonify({
            'success': True,
            'vertices': vertices,
            'indices': indices,
            'bounds': bounds,
            'vertex_count': len(vertices)//3,
            'face_count': len(indices)//3
        })
        
    except Exception as e:
        logger.error(f"获取网格几何数据失败: {str(e)}")
        return jsonify({'error': str(e)}), 500


def convert_tetrahedra_to_triangles(tetrahedra):
    """将四面体单元转换为三角形面"""
    triangles = []
    
    try:
        # 四面体的4个面的顶点索引组合
        tet_faces = [
            [0, 1, 2],  # 面1
            [0, 1, 3],  # 面2  
            [0, 2, 3],  # 面3
            [1, 2, 3]   # 面4
        ]
        
        if hasattr(tetrahedra, 'tolist'):
            tetrahedra = tetrahedra.tolist()
        
        # 处理四面体数据
        if isinstance(tetrahedra[0], list):
            # 如果是二维数组格式 [[tet1], [tet2], ...]
            for tet in tetrahedra:
                if len(tet) >= 4:  # 确保是四面体
                    for face in tet_faces:
                        triangles.extend([tet[face[0]], tet[face[1]], tet[face[2]]])
        else:
            # 如果是一维数组格式，每4个为一个四面体
            for i in range(0, len(tetrahedra), 4):
                if i + 3 < len(tetrahedra):
                    tet = tetrahedra[i:i+4]
                    for face in tet_faces:
                        triangles.extend([tet[face[0]], tet[face[1]], tet[face[2]]])
    
    except Exception as e:
        logger.error(f"转换四面体为三角形时出错: {e}")
        return []
    
    return triangles


def parse_mesh_export_file(file_path):
    """解析网格导出文件（备用方法）"""
    vertices = []
    indices = []
    
    try:
        with open(file_path, 'r') as f:
            content = f.read()
            # 这里需要根据实际导出格式解析
            # 这是一个简化的解析示例
            lines = content.split('\n')
            
            # 简单的解析逻辑，需要根据实际格式调整
            in_vertices = False
            in_elements = False
            
            for line in lines:
                if 'Vertex' in line or 'Node' in line:
                    in_vertices = True
                    in_elements = False
                elif 'Element' in line or 'Cell' in line:
                    in_vertices = False
                    in_elements = True
                elif in_vertices and len(line.strip()) > 0:
                    parts = line.strip().split()
                    if len(parts) >= 3:
                        vertices.extend([float(parts[0]), float(parts[1]), float(parts[2])])
                elif in_elements and len(line.strip()) > 0:
                    parts = line.strip().split()
                    if len(parts) >= 4:
                        # 转换为三角形
                        indices.extend([int(parts[0]), int(parts[1]), int(parts[2])])
                        indices.extend([int(parts[0]), int(parts[2]), int(parts[3])])
        
    except Exception as e:
        logger.error(f"解析网格文件失败: {e}")
    
    return vertices, indices