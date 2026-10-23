/**
 * 内容打包入口：把知识正文（TS 模块）与知识树、块索引汇总为单个 ESM 模块，
 * 供 Node 侧的导入脚本消费（Node 不能直接 import 带 @ 别名的 TS）。
 */
import { articlesByPoint as dsArticles } from '../client/src/content/knowledge-articles/books/data-structures'
import { articlesByPoint as coArticles } from '../client/src/content/knowledge-articles/books/computer-organization'
import { articlesByPoint as osArticles } from '../client/src/content/knowledge-articles/books/operating-systems'
import { articlesByPoint as cnArticles } from '../client/src/content/knowledge-articles/books/computer-network'
import {
  knowledgeBooks,
  allKnowledgePoints,
} from '../client/src/content/knowledge-tree'
import {
  subpointLocationByBlockId,
  blockIdsOfPoint,
  bookOfPoint,
} from '../client/src/content/knowledge-articles/block-index.generated'

export const articlesByBook = {
  'data-structures': dsArticles,
  'computer-organization': coArticles,
  'operating-systems': osArticles,
  'computer-network': cnArticles,
}

export { knowledgeBooks, allKnowledgePoints, subpointLocationByBlockId, blockIdsOfPoint, bookOfPoint }